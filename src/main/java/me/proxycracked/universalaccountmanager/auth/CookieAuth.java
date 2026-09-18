package me.proxycracked.universalaccountmanager.auth;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.Session;

/**
 * Login from exported browser cookies.
 *
 * <p>There is no Microsoft API that takes a cookie and returns a token: cookies only authenticate
 * at the web sign-in endpoints, so this has to walk the same pages a browser would. Three things
 * about that walk are worth knowing:</p>
 *
 * <ul>
 *   <li>The old three-hop chain through {@code sisu.xboxlive.com} stopped working - that endpoint
 *       now redirects to {@code login.microsoftonline.com/consumers} instead of
 *       {@code login.live.com}, so the fixed hop count ran out. It is kept only as a fallback.</li>
 *   <li>The primary path asks {@code login.live.com/oauth20_authorize.srf} directly, with the same
 *       MSA client the launcher uses ({@link MsaAuth}), for a code or a token. Unlike the redirect
 *       chain, that yields a <em>refresh token</em>, so the account keeps working afterwards.</li>
 *   <li>Microsoft answers parts of that flow with a 200 page whose form is posted by a one-line
 *       script rather than with a {@code Location} header. A browser does that for free; here it
 *       has to be done by hand, and not doing it was why cookies that work in a browser failed.</li>
 * </ul>
 */
public final class CookieAuth {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";
    private static final String SISU_URL = "https://sisu.xboxlive.com/connect/XboxLive/?state=login"
        + "&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d"
        + "&tid=896928775"
        + "&ru=https%3A%2F%2Fwww.minecraft.net%2Fen-us%2Flogin"
        + "&aid=1142970254";
    private static final String[] FOLLOWED_HOSTS = {
        "live.com", "microsoftonline.com", "microsoft.com", "xboxlive.com", "minecraft.net", "msauth.net", "msftauth.net"
    };
    private static final int MAX_HOPS = 20;
    private static final int MAX_BODY_BYTES = 512 * 1024;

    private CookieAuth() {
    }

    public static CompletableFuture<CookieResult> loginFromFile(File cookieFile, Consumer<String> status, Executor executor) {
        return CompletableFuture.supplyAsync(new Supplier<CookieResult>() {
            @Override
            public CookieResult get() {
                try {
                    report(status, "&fReading cookie file...&r");
                    CookieJar jar = CookieJar.fromNetscapeFile(cookieFile);
                    if (jar.isEmpty()) {
                        jar = CookieJar.fromText(readFile(cookieFile));
                    }
                    return run(jar, "file", status);
                } catch (InterruptedException interrupted) {
                    throw new CancellationException("Cookie login cancelled!");
                } catch (Exception error) {
                    throw new CompletionException("Cookie login failed!", error);
                }
            }
        }, executor);
    }

    /** Same login, from pasted text: a Netscape export, an extension's JSON, or a Cookie header. */
    public static CompletableFuture<CookieResult> loginFromText(String cookieText, Consumer<String> status, Executor executor) {
        return CompletableFuture.supplyAsync(new Supplier<CookieResult>() {
            @Override
            public CookieResult get() {
                try {
                    report(status, "&fReading pasted cookies...&r");
                    return run(CookieJar.fromText(cookieText), "clipboard", status);
                } catch (InterruptedException interrupted) {
                    throw new CancellationException("Cookie login cancelled!");
                } catch (Exception error) {
                    throw new CompletionException("Cookie login failed!", error);
                }
            }
        }, executor);
    }

    private static CookieResult run(CookieJar jar, String source, Consumer<String> status) throws Exception {
        if (jar.isEmpty()) {
            throw new Exception("clipboard".equals(source)
                ? "No cookies in that text - paste a cookies.txt export, an extension's JSON, or a Cookie header"
                : "No cookies in that file - it must be a Netscape/cookies.txt export");
        }
        if (!jar.hasMicrosoftCookies()) {
            throw new Exception("No Microsoft cookies here - export them while signed in to login.live.com");
        }

        Diagnosis diagnosis = new Diagnosis(jar);

        // Both grants, silent first. The code grant is the normal one; the implicit grant is what
        // the single-cookie files the alt shops hand out are made for, and it answers with the
        // tokens straight in the redirect fragment.
        //
        // Each attempt gets its own copy of the jar: a refused sign-in comes back with Set-Cookie
        // headers of its own, and letting those accumulate is what made a second attempt behave
        // differently from the first.
        boolean[] implicitFlow = {false, true, false, true};
        boolean[] silentFlow = {true, true, false, false};
        for (int attempt = 0; attempt < implicitFlow.length; attempt++) {
            boolean implicit = implicitFlow[attempt];
            boolean silent = silentFlow[attempt];
            report(status, implicit
                ? "&fAsking Microsoft for a token...&r"
                : "&fAsking Microsoft for an authorization code...&r");

            String landing = chaseToRedirect(MsaAuth.authorizeUrl(implicit ? "token" : "code", silent), jar.copy(), diagnosis);
            if (landing == null) {
                continue;
            }

            if (implicit) {
                String accessToken = parameter(landing, "access_token");
                if (accessToken != null && !accessToken.isEmpty()) {
                    report(status, "&fSigning in to Xbox Live...&r");
                    String refreshToken = parameter(landing, "refresh_token");
                    Session session = MsaAuth.minecraftLogin(accessToken);
                    return new CookieResult(session, session.getToken(), refreshToken == null ? "" : refreshToken);
                }
            } else {
                String code = parameter(landing, "code");
                if (code != null && !code.isEmpty()) {
                    report(status, "&fExchanging the code for tokens...&r");
                    MsaAuth.TokenPair tokens = MsaAuth.exchangeAuthCode(code);
                    report(status, "&fSigning in to Xbox Live...&r");
                    Session session = MsaAuth.minecraftLogin(tokens.getAccessToken());
                    return new CookieResult(session, session.getToken(), tokens.getRefreshToken());
                }
            }
        }

        report(status, "&fTrying the Xbox sign-in redirect chain...&r");
        try {
            return legacyChain(jar.copy(), status, diagnosis);
        } catch (Exception chainFailed) {
            throw new Exception(diagnosis.describe());
        }
    }

    /**
     * Follows the redirects until Microsoft lands on the desktop redirect.
     *
     * @return the landing URL, whatever it carries, or null if the chain never got there
     */
    private static String chaseToRedirect(String startUrl, CookieJar jar, Diagnosis diagnosis) throws Exception {
        String url = startUrl;
        String postBody = null;
        for (int hop = 0; hop < MAX_HOPS && url != null; hop++) {
            if (url.startsWith(MsaAuth.REDIRECT_URI)) {
                diagnosis.landed(url);
                return url;
            }

            Response response = request(url, postBody, jar);
            postBody = null;

            if (response.location != null) {
                url = absolute(url, response.location.replace(" ", "%20"));
                continue;
            }

            Form form = autoSubmitForm(response.body);
            if (form != null && canFollow(url, absolute(url, form.action))) {
                url = absolute(url, form.action);
                postBody = form.fields;
                continue;
            }

            String scripted = scriptedRedirect(response.body);
            if (scripted != null && canFollow(url, absolute(url, scripted))) {
                url = absolute(url, scripted);
                continue;
            }

            diagnosis.pageShown(new URL(url).getHost(), classify(response.body));
            return null;
        }
        return null;
    }

    /** The pre-2025 flow: bounce through Xbox sign-in and read the XBL token out of the final URL. */
    private static CookieResult legacyChain(CookieJar jar, Consumer<String> status, Diagnosis diagnosis) throws Exception {
        String url = SISU_URL;
        String postBody = null;
        String tokenUrl = null;
        for (int hop = 0; hop < MAX_HOPS && url != null; hop++) {
            if (url.contains("accessToken=")) {
                tokenUrl = url;
                break;
            }

            Response response = request(url, postBody, jar);
            postBody = null;

            if (response.location != null) {
                url = absolute(url, response.location.replace(" ", "%20"));
                continue;
            }

            Form form = autoSubmitForm(response.body);
            if (form != null && canFollow(url, absolute(url, form.action))) {
                url = absolute(url, form.action);
                postBody = form.fields;
                continue;
            }

            String scripted = scriptedRedirect(response.body);
            if (scripted != null && canFollow(url, absolute(url, scripted))) {
                url = absolute(url, scripted);
                continue;
            }

            diagnosis.pageShown(new URL(url).getHost(), classify(response.body));
            break;
        }
        if (tokenUrl == null) {
            throw new Exception(diagnosis.describe());
        }

        report(status, "&fExtracting access token...&r");
        String encoded = tokenUrl.substring(tokenUrl.indexOf("accessToken=") + "accessToken=".length());
        int ampersand = encoded.indexOf('&');
        if (ampersand >= 0) {
            encoded = encoded.substring(0, ampersand);
        }
        String decoded = new String(Base64.getDecoder().decode(URLDecoder.decode(encoded, "UTF-8")), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\"rp://api.minecraftservices.com/\",");
        if (parts.length < 2) {
            throw new Exception("Failed to decode the Xbox access token");
        }
        String rest = parts[1];
        String token = rest.split("\"Token\":\"")[1].split("\"")[0];
        String uhs = rest.split(Pattern.quote("{\"DisplayClaims\":{\"xui\":[{\"uhs\":\""))[1].split("\"")[0];

        report(status, "&fLogging into Minecraft services...&r");
        Session session = MsaAuth.loginWithIdentityToken("XBL3.0 x=" + uhs + ";" + token);
        return new CookieResult(session, session.getToken(), "");
    }

    private static Response request(String url, String postBody, CookieJar jar) throws IOException {
        URL target = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setRequestMethod(postBody == null ? "GET" : "POST");
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "identity");
        String cookies = jar.headerFor(target);
        if (!cookies.isEmpty()) {
            connection.setRequestProperty("Cookie", cookies);
        }

        try {
            if (postBody == null) {
                connection.connect();
            } else {
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(postBody.getBytes(StandardCharsets.UTF_8));
                } finally {
                    output.close();
                }
            }
            jar.capture(connection);
            String location = connection.getHeaderField("Location");
            int status = connection.getResponseCode();
            String body = location == null ? readBody(connection) : "";
            return new Response(status, location, body);
        } finally {
            connection.disconnect();
        }
    }

    /** Reads a sign-in page, so the chase can follow whatever it does instead of redirecting. */
    private static String readBody(HttpURLConnection connection) {
        InputStream stream = null;
        try {
            stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while (buffer.size() < MAX_BODY_BYTES && (read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing to do.
                }
            }
        }
    }

    /**
     * The auto-submitting form Microsoft answers with instead of a redirect.
     *
     * <p>A page asking for a password or an account choice is not one of these - there is nothing
     * to submit on the user's behalf - so those are left alone and reported instead.</p>
     */
    private static Form autoSubmitForm(String body) {
        if (body.isEmpty() || needsUser(body)) {
            return null;
        }
        Matcher form = Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE).matcher(body);
        while (form.find()) {
            String tag = form.group();
            String method = attribute(tag, "method");
            if (method == null || !"post".equalsIgnoreCase(method)) {
                continue;
            }
            String action = attribute(tag, "action");
            if (action == null || action.isEmpty()) {
                continue;
            }

            StringBuilder fields = new StringBuilder();
            Matcher input = Pattern.compile("<input[^>]*>", Pattern.CASE_INSENSITIVE).matcher(body);
            while (input.find()) {
                String inputTag = input.group();
                String type = attribute(inputTag, "type");
                String name = attribute(inputTag, "name");
                if (name == null || (type != null && !"hidden".equalsIgnoreCase(type))) {
                    continue;
                }
                String value = attribute(inputTag, "value");
                if (fields.length() > 0) {
                    fields.append('&');
                }
                fields.append(urlEncode(name)).append('=').append(urlEncode(value == null ? "" : value));
            }
            return new Form(action, fields.toString());
        }
        return null;
    }

    /** A page that navigates with a meta refresh or a script instead of a form post. */
    private static String scriptedRedirect(String body) {
        if (body.isEmpty() || needsUser(body)) {
            return null;
        }
        Pattern[] patterns = {
            Pattern.compile("<meta[^>]+http-equiv=[\"']?refresh[\"']?[^>]+url=([^\"'>]+)[\"'>]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("location\\.replace\\([\"']([^\"']+)[\"']\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("location\\.href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return unescape(matcher.group(1));
            }
        }
        return null;
    }

    /** True when the page is asking the person for something the mod cannot supply. */
    private static boolean needsUser(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("type=\"password\"")
            || lower.contains("type='password'")
            || lower.contains("name=\"passwd\"")
            || lower.contains("id=\"i0116\"")
            || lower.contains("accounttile")
            || lower.contains("pick an account");
    }

    /** What the page was, for the failure message. */
    private static String classify(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("pick an account") || lower.contains("accounttile")) {
            return "an account picker - the export has more than one signed-in account";
        }
        if (needsUser(body)) {
            return "a sign-in page asking for a password";
        }
        return "a page with no redirect in it";
    }

    private static String attribute(String tag, String name) {
        Matcher quoted = Pattern.compile("\\b" + name + "\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag);
        if (quoted.find()) {
            return unescape(quoted.group(1));
        }
        Matcher bare = Pattern.compile("\\b" + name + "\\s*=\\s*([^\\s>]+)", Pattern.CASE_INSENSITIVE).matcher(tag);
        return bare.find() ? unescape(bare.group(1)) : null;
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&#38;", "&").replace("\\/", "/").trim();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    /** Staying on the current host is always fine; otherwise only Microsoft's own sign-in hosts. */
    private static boolean canFollow(String from, String to) {
        try {
            if (new URL(from).getHost().equalsIgnoreCase(new URL(to).getHost())) {
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return isMicrosoftHost(to);
    }

    /** Only Microsoft's own sign-in hosts are worth following. */
    private static boolean isMicrosoftHost(String url) {
        try {
            String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
            for (String suffix : FOLLOWED_HOSTS) {
                if (host.equals(suffix) || host.endsWith("." + suffix)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static String absolute(String base, String location) throws IOException {
        return new URL(new URL(base), location).toString();
    }

    /** Reads a query or fragment parameter out of a URL. */
    private static String parameter(String url, String name) {
        for (String section : splitQueryAndFragment(url)) {
            for (String pair : section.split("&")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                if (!pair.substring(0, equals).equals(name)) {
                    continue;
                }
                try {
                    return URLDecoder.decode(pair.substring(equals + 1), "UTF-8");
                } catch (Exception ignored) {
                    return pair.substring(equals + 1);
                }
            }
        }
        return null;
    }

    private static String[] splitQueryAndFragment(String url) {
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        String queryPart = query < 0 ? "" : url.substring(query + 1, fragment > query ? fragment : url.length());
        String fragmentPart = fragment < 0 ? "" : url.substring(fragment + 1);
        return new String[] {queryPart, fragmentPart};
    }

    private static void report(Consumer<String> status, String message) {
        if (status != null) {
            status.accept(message);
        }
    }

    private static String readFile(File file) throws IOException {
        byte[] bytes = new byte[(int) Math.min(file.length(), 4L * 1024L * 1024L)];
        java.io.DataInputStream stream = new java.io.DataInputStream(new java.io.FileInputStream(file));
        try {
            stream.readFully(bytes);
        } finally {
            stream.close();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Kept for callers of the pre-2.13 API. */
    public static Map<String, String> parseCookieFile(File file) throws IOException {
        return CookieJar.fromNetscapeFile(file).loginLiveCookies();
    }

    /** Kept for callers of the pre-2.13 API. */
    public static String buildCookieString(Map<String, String> cookies) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : new HashMap<String, String>(cookies).entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    /**
     * Collects what Microsoft actually said, so a failure names the reason.
     *
     * <p>"Microsoft did not accept these cookies" was a guess, and the real causes want different
     * answers from the user: an export made with JavaScript has none of the sign-in cookies in it,
     * an expired session comes back with {@code login_required}, and an export holding two signed-in
     * accounts stops at an account picker that only a human can answer.</p>
     */
    private static final class Diagnosis {
        // Snapshotted before the first request: the chain itself picks up Microsoft's own cookies,
        // which would otherwise inflate what the file was reported to contain.
        private final boolean hadSignInCookies;
        private final int microsoftCookies;
        private String microsoftError;
        private String microsoftErrorDescription;
        private String interactiveHost;
        private String interactivePage;

        private Diagnosis(CookieJar jar) {
            this.hadSignInCookies = jar.hasSignInCookies();
            this.microsoftCookies = jar.microsoftCookieCount();
        }

        private void landed(String url) {
            String error = parameter(url, "error");
            if (error != null && !error.isEmpty() && microsoftError == null) {
                microsoftError = error;
                String description = parameter(url, "error_description");
                while (description != null && description.endsWith(".")) {
                    description = description.substring(0, description.length() - 1);
                }
                microsoftErrorDescription = description;
            }
        }

        private void pageShown(String host, String page) {
            if (interactiveHost == null) {
                interactiveHost = host;
                interactivePage = page;
            }
        }

        private String describe() {
            if (!hadSignInCookies) {
                return "These cookies have no Microsoft sign-in cookie in them ("
                    + microsoftCookies + " Microsoft cookies, none of "
                    + CookieJar.SIGN_IN_COOKIE_NAMES + "). The sign-in cookies are HttpOnly, so a "
                    + "document.cookie copy cannot see them - export with a cookies.txt extension instead";
            }
            if (microsoftError != null) {
                String detail = microsoftErrorDescription == null || microsoftErrorDescription.isEmpty()
                    ? "" : " - " + microsoftErrorDescription;
                return "Microsoft rejected the cookies (" + microsoftError + ")" + detail
                    + ". They are expired or signed out; export a fresh cookies.txt";
            }
            if (interactiveHost != null) {
                return "Microsoft answered " + interactiveHost + " with " + interactivePage
                    + ", so the sign-in could not be finished here. Use device-code login for this account";
            }
            return "Microsoft did not accept these cookies - export a fresh cookies.txt while signed in";
        }
    }

    private static final class Form {
        private final String action;
        private final String fields;

        private Form(String action, String fields) {
            this.action = action;
            this.fields = fields;
        }
    }

    private static final class Response {
        private final int status;
        private final String location;
        private final String body;

        private Response(int status, String location, String body) {
            this.status = status;
            this.location = location;
            this.body = body == null ? "" : body;
        }

        @Override
        public String toString() {
            return status + " -> " + (location == null ? body.length() + " bytes" : location);
        }
    }

    public static class CookieResult {
        public final Session session;
        public final String accessToken;
        /** Empty when the login came from the fallback chain, which cannot mint one. */
        public final String refreshToken;

        public CookieResult(Session session, String accessToken, String refreshToken) {
            this.session = session;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
        }
    }
}
