package me.proxycracked.universalaccountmanager.auth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A cookie jar for the browser-export login flow.
 *
 * <p>The old implementation kept a flat map of {@code login.live.com} cookies and sent it to every
 * hop. Microsoft now bounces the Xbox sign-in through {@code login.microsoftonline.com} as well, so
 * cookies have to be matched per host and the ones each hop sets have to be carried forward, or the
 * chain drops out at the first redirect.</p>
 */
public final class CookieJar {
    private final List<Cookie> cookies = new ArrayList<Cookie>();

    /** Reads a Netscape/{@code cookies.txt} export, keeping every domain in the file. */
    public static CookieJar fromNetscapeFile(File file) throws IOException {
        CookieJar jar = new CookieJar();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                jar.readNetscapeLine(line);
            }
        } finally {
            reader.close();
        }
        return jar;
    }

    /**
     * Reads whatever shape of cookies was pasted in.
     *
     * <p>Clipboards carry three: a Netscape export, the JSON an extension like Cookie-Editor or
     * EditThisCookie copies, or the raw {@code name=value; name=value} request header out of the
     * browser dev tools. The header form has no domains in it, so those are filed under
     * {@code login.live.com}, which is where they come from.</p>
     */
    public static CookieJar fromText(String text) {
        return fromText(text, "login.live.com");
    }

    /**
     * @param defaultDomain domain for entries that carry none, or null to skip them. Pasted cookies
     *                      are assumed to be Microsoft's; a file being scanned has to say so itself,
     *                      or every JSON file with {@code name}/{@code value} fields in it would
     *                      look like a cookie export.
     */
    public static CookieJar fromText(String text, String defaultDomain) {
        CookieJar jar = new CookieJar();
        String content = text == null ? "" : text.trim();
        if (content.isEmpty()) {
            return jar;
        }

        if (content.startsWith("[") || content.startsWith("{")) {
            jar.readJson(content, defaultDomain);
            return jar;
        }
        if (defaultDomain == null) {
            // Only the JSON shape is worth guessing at inside a file; a bare Cookie header in a
            // .txt would be indistinguishable from prose.
            return jar;
        }

        boolean readAnyLine = false;
        for (String line : content.split("[\\r\\n]+")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int before = jar.cookies.size();
            jar.readNetscapeLine(line);
            readAnyLine |= jar.cookies.size() > before;
        }
        if (!readAnyLine) {
            jar.readCookieHeader(content);
        }
        return jar;
    }

    private void readJson(String content, String defaultDomain) {
        try {
            JsonElement root = new JsonParser().parse(content);
            JsonArray entries;
            if (root.isJsonArray()) {
                entries = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("cookies")) {
                entries = root.getAsJsonObject().getAsJsonArray("cookies");
            } else {
                return;
            }
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String name = AuthHttp.optionalString(entry, "name");
                String value = AuthHttp.optionalString(entry, "value");
                if (name.isEmpty()) {
                    continue;
                }
                String domain = AuthHttp.optionalString(entry, "domain");
                if (domain.isEmpty()) {
                    if (defaultDomain == null) {
                        continue;
                    }
                    domain = defaultDomain;
                }
                String path = AuthHttp.optionalString(entry, "path");
                put(new Cookie(strip(domain), domain.startsWith("."), path.isEmpty() ? "/" : path, name, value));
            }
        } catch (RuntimeException ignored) {
            // Not the JSON shape we know; the caller reports "no cookies found".
        }
    }

    private void readCookieHeader(String header) {
        // Header form carries no domains at all, so these are filed under login.live.com.
        String content = header;
        if (content.toLowerCase(Locale.ROOT).startsWith("cookie:")) {
            content = content.substring("cookie:".length());
        }
        for (String pair : content.split(";")) {
            String trimmed = pair.trim();
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            put(new Cookie(
                "login.live.com",
                true,
                "/",
                trimmed.substring(0, equals).trim(),
                trimmed.substring(equals + 1).trim()
            ));
        }
    }

    private void readNetscapeLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return;
        }
        // curl and yt-dlp mark HttpOnly cookies with a comment-looking prefix.
        if (line.startsWith("#HttpOnly_")) {
            line = line.substring("#HttpOnly_".length());
        } else if (line.startsWith("#")) {
            return;
        }

        String[] parts = line.split("\t", -1);
        if (parts.length < 7) {
            parts = line.split("\\s+", 7);
        }
        if (parts.length < 7) {
            return;
        }

        String domain = parts[0].trim();
        boolean includeSubdomains = domain.startsWith(".") || "TRUE".equalsIgnoreCase(parts[1].trim());
        String path = parts[2].trim().isEmpty() ? "/" : parts[2].trim();
        String name = parts[5].trim();
        String value = parts[6].trim();
        if (name.isEmpty() || domain.isEmpty()) {
            return;
        }
        put(new Cookie(strip(domain), includeSubdomains, path, name, value));
    }

    /** Stores the cookies a response set, so later hops in the same chain see them. */
    public void capture(HttpURLConnection connection) {
        String host = connection.getURL().getHost().toLowerCase(Locale.ROOT);
        for (int index = 0; ; index++) {
            String key = connection.getHeaderFieldKey(index);
            String value = connection.getHeaderField(index);
            if (key == null && value == null) {
                break;
            }
            if (key == null || !"set-cookie".equalsIgnoreCase(key)) {
                continue;
            }
            readSetCookie(value, host);
        }
    }

    private void readSetCookie(String header, String requestHost) {
        if (header == null || header.trim().isEmpty()) {
            return;
        }
        String[] segments = header.split(";");
        String pair = segments[0].trim();
        int equals = pair.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();

        String domain = requestHost;
        boolean includeSubdomains = false;
        String path = "/";
        for (int index = 1; index < segments.length; index++) {
            String attribute = segments[index].trim();
            String lower = attribute.toLowerCase(Locale.ROOT);
            if (lower.startsWith("domain=")) {
                domain = strip(attribute.substring("domain=".length()).trim());
                includeSubdomains = true;
            } else if (lower.startsWith("path=")) {
                path = attribute.substring("path=".length()).trim();
                if (path.isEmpty()) {
                    path = "/";
                }
            } else if (lower.startsWith("max-age=") && lower.endsWith("=0")) {
                value = "";
            } else if (lower.startsWith("expires=") && lower.contains("1970")) {
                value = "";
            }
        }
        put(new Cookie(domain, includeSubdomains, path, name, value, false));
    }

    private void put(Cookie cookie) {
        for (int index = 0; index < cookies.size(); index++) {
            Cookie existing = cookies.get(index);
            if (!existing.matchesIdentity(cookie)) {
                continue;
            }
            // A response that signs us out, or hands back a fresh value for a cookie we were given,
            // must not take the export's credentials with it - a failed attempt would otherwise
            // leave the next one with nothing to authenticate with.
            if (existing.fromSource && !cookie.fromSource) {
                return;
            }
            cookies.set(index, cookie);
            return;
        }
        cookies.add(cookie);
    }

    /** An independent jar holding the same cookies, so one attempt cannot affect the next. */
    public CookieJar copy() {
        CookieJar copy = new CookieJar();
        copy.cookies.addAll(cookies);
        return copy;
    }

    /** Builds the {@code Cookie} header for one request, most specific match per name. */
    public String headerFor(URL url) {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        String path = url.getPath() == null || url.getPath().isEmpty() ? "/" : url.getPath();

        Map<String, Cookie> best = new LinkedHashMap<String, Cookie>();
        for (Cookie cookie : cookies) {
            if (cookie.value.isEmpty() || !cookie.matchesHost(host) || !cookie.matchesPath(path)) {
                continue;
            }
            Cookie current = best.get(cookie.name);
            if (current == null || cookie.isMoreSpecificThan(current)) {
                best.put(cookie.name, cookie);
            }
        }

        StringBuilder header = new StringBuilder();
        for (Cookie cookie : best.values()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(cookie.name).append('=').append(cookie.value);
        }
        return header.toString();
    }

    public boolean isEmpty() {
        return cookies.isEmpty();
    }

    public int size() {
        return cookies.size();
    }

    /**
     * The cookies that actually carry an MSA session. All of them are HttpOnly, which is why an
     * export made from {@code document.cookie} looks full but authenticates nothing.
     */
    public static final String SIGN_IN_COOKIE_NAMES = "MSPAuth, MSPProf, RPSSecAuth, WLSSC, MSAAUTH, ESTSAUTH";
    private static final String[] SIGN_IN_COOKIES = {
        "mspauth", "mspprof", "rpssecauth", "wlssc", "msaauth", "__host-msaauth", "__secure-msaauth",
        "estsauth", "estsauthpersistent", "estsauthlight"
    };

    /** True when the cookies carried a Microsoft sign-in at all, worth saying out loud on failure. */
    public boolean hasMicrosoftCookies() {
        return microsoftCookieCount() > 0;
    }

    /** True when at least one cookie can actually authenticate, rather than just identify. */
    public boolean hasSignInCookies() {
        for (Cookie cookie : cookies) {
            String name = cookie.name.toLowerCase(Locale.ROOT);
            for (String signIn : SIGN_IN_COOKIES) {
                if (name.equals(signIn)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many Microsoft sign-in cookies are in here, shown next to each scanned file. */
    public int microsoftCookieCount() {
        int count = 0;
        for (Cookie cookie : cookies) {
            if (cookie.domain.endsWith("live.com")
                || cookie.domain.endsWith("microsoftonline.com")
                || cookie.domain.endsWith("microsoft.com")) {
                count++;
            }
        }
        return count;
    }

    /** Flat {@code name=value} view of the login.live.com cookies, for callers of the old API. */
    public Map<String, String> loginLiveCookies() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Cookie cookie : cookies) {
            if (cookie.domain.endsWith("login.live.com") && !map.containsKey(cookie.name)) {
                map.put(cookie.name, cookie.value);
            }
        }
        return map;
    }

    public void putAll(Map<String, String> nameToValue, String domain) {
        for (Map.Entry<String, String> entry : nameToValue.entrySet()) {
            put(new Cookie(strip(domain), true, "/", entry.getKey(), entry.getValue()));
        }
    }

    private static String strip(String domain) {
        String value = domain.toLowerCase(Locale.ROOT).trim();
        return value.startsWith(".") ? value.substring(1) : value;
    }

    private static final class Cookie {
        private final String domain;
        private final boolean includeSubdomains;
        private final String path;
        private final String name;
        private final String value;
        /** True for cookies that came from the export: those are the credentials, never overwrite them. */
        private final boolean fromSource;

        private Cookie(String domain, boolean includeSubdomains, String path, String name, String value) {
            this(domain, includeSubdomains, path, name, value, true);
        }

        private Cookie(String domain, boolean includeSubdomains, String path, String name, String value, boolean fromSource) {
            this.domain = domain;
            this.includeSubdomains = includeSubdomains;
            this.path = path;
            this.name = name;
            this.value = value;
            this.fromSource = fromSource;
        }

        private boolean matchesIdentity(Cookie other) {
            return name.equals(other.name) && domain.equals(other.domain) && path.equals(other.path);
        }

        private boolean matchesHost(String host) {
            if (host.equals(domain)) {
                return true;
            }
            return includeSubdomains && host.endsWith("." + domain);
        }

        private boolean matchesPath(String requestPath) {
            return requestPath.startsWith(path) || "/".equals(path);
        }

        private boolean isMoreSpecificThan(Cookie other) {
            if (domain.length() != other.domain.length()) {
                return domain.length() > other.domain.length();
            }
            return path.length() > other.path.length();
        }
    }
}
