package me.proxycracked.universalaccountmanager.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;

import net.minecraft.util.Session;

/**
 * Microsoft login against the legacy MSA client ({@code 00000000402b5328}) that the vanilla
 * launcher and the alt shops use.
 *
 * <p>This is the client Localts (and every other refresh-token seller) mints tokens with, and the
 * only one of the two clients in this mod that still supports the device-code flow, so it is the
 * primary Microsoft path now. Its tokens need the {@code t=} RpsTicket prefix at the Xbox
 * user-authenticate step; the bundled Azure app ({@link MicrosoftAuth}) uses {@code d=} and cannot
 * redeem them.</p>
 */
public final class MsaAuth {
    public static final String CLIENT_ID = "00000000402b5328";
    public static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    public static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    public static final String AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf";
    public static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    public static final String CONNECT_URL = "https://login.live.com/oauth20_connect.srf";

    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private MsaAuth() {
    }

    /**
     * Authorization URL for the cookie flow.
     *
     * @param responseType {@code code} for the authorization-code grant, {@code token} for the
     *                     implicit grant - the one the alt shops' single-cookie files are made for,
     *                     which returns the tokens in the redirect fragment
     * @param silent       adds {@code prompt=none}, so Microsoft answers with an error instead of a
     *                     sign-in page when the cookies are not enough
     */
    public static String authorizeUrl(String responseType, boolean silent) {
        return AUTHORIZE_URL
            + "?client_id=" + CLIENT_ID
            + "&response_type=" + responseType
            + "&scope=" + urlEncode(SCOPE)
            + "&redirect_uri=" + urlEncode(REDIRECT_URI)
            + (silent ? "&prompt=none" : "");
    }

    /** Silent-authorization URL: with valid login.live.com cookies this answers with a code. */
    public static String silentAuthorizeUrl() {
        return authorizeUrl("code", true);
    }

    /** Same authorization request, but allowed to show a sign-in page if the cookies fall short. */
    public static String interactiveAuthorizeUrl() {
        return authorizeUrl("code", false);
    }

    public static TokenPair refresh(String refreshToken) throws Exception {
        if (StringUtils.isBlank(refreshToken)) {
            throw new Exception("Refresh token is empty");
        }
        HttpPost request = new HttpPost(URI.create(TOKEN_URL));
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new UrlEncodedFormEntity(Arrays.asList(
            new BasicNameValuePair("client_id", CLIENT_ID),
            new BasicNameValuePair("grant_type", "refresh_token"),
            new BasicNameValuePair("refresh_token", refreshToken.trim()),
            new BasicNameValuePair("redirect_uri", REDIRECT_URI),
            new BasicNameValuePair("scope", SCOPE)
        ), "UTF-8"));
        return TokenPair.of(AuthHttp.execute(request, "Microsoft token refresh"), refreshToken.trim());
    }

    public static TokenPair exchangeAuthCode(String code) throws Exception {
        if (StringUtils.isBlank(code)) {
            throw new Exception("Authorization code is empty");
        }
        HttpPost request = new HttpPost(URI.create(TOKEN_URL));
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new UrlEncodedFormEntity(Arrays.asList(
            new BasicNameValuePair("client_id", CLIENT_ID),
            new BasicNameValuePair("grant_type", "authorization_code"),
            new BasicNameValuePair("code", code.trim()),
            new BasicNameValuePair("redirect_uri", REDIRECT_URI),
            new BasicNameValuePair("scope", SCOPE)
        ), "UTF-8"));
        return TokenPair.of(AuthHttp.execute(request, "Microsoft code exchange"), "");
    }

    /** Starts the device-code flow. The Azure app this mod ships cannot do this; the MSA client can. */
    public static DeviceCode requestDeviceCode() throws Exception {
        HttpPost request = new HttpPost(URI.create(CONNECT_URL));
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new UrlEncodedFormEntity(Arrays.asList(
            new BasicNameValuePair("client_id", CLIENT_ID),
            new BasicNameValuePair("scope", SCOPE),
            new BasicNameValuePair("response_type", "device_code")
        ), "UTF-8"));
        JsonObject json = AuthHttp.execute(request, "Device code request");
        return new DeviceCode(
            AuthHttp.requiredString(json, "user_code", "Microsoft did not return a user code"),
            AuthHttp.requiredString(json, "device_code", "Microsoft did not return a device code"),
            defaulted(AuthHttp.optionalString(json, "verification_uri"), "https://www.microsoft.com/link"),
            AuthHttp.optionalInt(json, "interval", 5),
            AuthHttp.optionalInt(json, "expires_in", 900)
        );
    }

    /**
     * Polls the device-code grant once.
     *
     * @return the tokens, or {@code null} while the user has not finished signing in yet.
     */
    public static TokenPair pollDeviceCode(String deviceCode) throws Exception {
        HttpPost request = new HttpPost(URI.create(TOKEN_URL));
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new UrlEncodedFormEntity(Arrays.asList(
            new BasicNameValuePair("client_id", CLIENT_ID),
            new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
            new BasicNameValuePair("device_code", deviceCode)
        ), "UTF-8"));

        AuthHttp.Response response = AuthHttp.send(request, "Device code poll");
        String error = AuthHttp.optionalString(response.json, "error");
        if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
            return null;
        }
        if ("expired_token".equals(error) || "code_expired".equals(error)) {
            throw new Exception("The sign-in code expired, start again");
        }
        if ("authorization_declined".equals(error) || "access_denied".equals(error)) {
            throw new Exception("Sign-in was declined in the browser");
        }
        if (response.status < 200 || response.status >= 300) {
            throw new Exception("Device sign-in failed: " + AuthHttp.describe(response.json, response.body));
        }
        if (response.json == null) {
            throw new Exception("Device sign-in returned a non-JSON response: " + AuthHttp.snippet(response.body));
        }
        return TokenPair.of(response.json, "");
    }

    /** Runs Xbox Live -&gt; XSTS -&gt; Minecraft services -&gt; profile with an MSA access token. */
    public static Session minecraftLogin(String msaAccessToken) throws Exception {
        JsonObject xbl = acquireXboxToken(msaAccessToken);
        String xblToken = AuthHttp.requiredString(xbl, "Token", "Xbox Live did not return a token");

        JsonObject xsts = acquireXstsToken(xblToken);
        String xstsToken = AuthHttp.requiredString(xsts, "Token", "XSTS did not return a token");
        String userHash = displayClaimUhs(xsts);

        JsonObject minecraft = loginWithXbox("XBL3.0 x=" + userHash + ";" + xstsToken);
        String accessToken = AuthHttp.requiredString(minecraft, "access_token", "Minecraft services rejected the Xbox token");

        return profileSession(accessToken);
    }

    /** Trades an {@code XBL3.0 x=<uhs>;<token>} identity token for a Minecraft session. */
    public static Session loginWithIdentityToken(String identityToken) throws Exception {
        JsonObject minecraft = loginWithXbox(identityToken);
        String accessToken = AuthHttp.requiredString(minecraft, "access_token", "Minecraft services rejected the Xbox token");
        return profileSession(accessToken);
    }

    /** Turns a Minecraft access token into a session, i.e. the "does this account own the game" check. */
    public static Session profileSession(String minecraftAccessToken) throws Exception {
        HttpGet request = new HttpGet(URI.create(MC_PROFILE_URL));
        request.setHeader("Authorization", "Bearer " + minecraftAccessToken);
        JsonObject profile = AuthHttp.execute(request, "Minecraft profile lookup");
        String name = AuthHttp.requiredString(profile, "name", "Minecraft profile has no username (the account may not own the game)");
        String id = AuthHttp.requiredString(profile, "id", "Minecraft profile has no UUID");
        return new Session(name, id, minecraftAccessToken, Session.Type.MOJANG.toString());
    }

    private static JsonObject acquireXboxToken(String msaAccessToken) throws Exception {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        // MBI_SSL tokens use the "t=" ticket prefix; "d=" is only for Azure/MSAL access tokens.
        properties.addProperty("RpsTicket", "t=" + msaAccessToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");
        return AuthHttp.execute(jsonPost(XBL_URL, body), "Xbox Live authentication");
    }

    private static JsonObject acquireXstsToken(String xblToken) throws Exception {
        JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(xblToken));

        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");
        return AuthHttp.execute(jsonPost(XSTS_URL, body), "XSTS authorization");
    }

    private static JsonObject loginWithXbox(String identityToken) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", identityToken);
        body.addProperty("ensureLegacyEnabled", true);
        return AuthHttp.execute(jsonPost(MC_LOGIN_URL, body), "Minecraft login");
    }

    private static HttpPost jsonPost(String url, JsonObject body) throws Exception {
        HttpPost request = new HttpPost(URI.create(url));
        request.setHeader("Content-Type", "application/json; charset=utf-8");
        request.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8.name()));
        return request;
    }

    private static String displayClaimUhs(JsonObject xsts) throws Exception {
        JsonElement claims = xsts.get("DisplayClaims");
        if (claims != null && claims.isJsonObject()) {
            JsonElement xui = claims.getAsJsonObject().get("xui");
            if (xui != null && xui.isJsonArray() && xui.getAsJsonArray().size() > 0) {
                JsonElement first = xui.getAsJsonArray().get(0);
                if (first.isJsonObject()) {
                    String uhs = AuthHttp.optionalString(first.getAsJsonObject(), "uhs");
                    if (!StringUtils.isBlank(uhs)) {
                        return uhs;
                    }
                }
            }
        }
        throw new Exception("XSTS response did not contain a user hash");
    }

    private static String defaulted(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    public static final class TokenPair {
        private final String accessToken;
        private final String refreshToken;

        private TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        static TokenPair of(JsonObject json, String previousRefreshToken) throws Exception {
            String accessToken = AuthHttp.requiredString(json, "access_token", "Microsoft did not return an access token");
            String refreshToken = AuthHttp.optionalString(json, "refresh_token");
            return new TokenPair(accessToken, StringUtils.isBlank(refreshToken) ? previousRefreshToken : refreshToken);
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }

    public static final class DeviceCode {
        private final String userCode;
        private final String deviceCode;
        private final String verificationUri;
        private final int intervalSeconds;
        private final int expiresInSeconds;

        DeviceCode(String userCode, String deviceCode, String verificationUri, int intervalSeconds, int expiresInSeconds) {
            this.userCode = userCode;
            this.deviceCode = deviceCode;
            this.verificationUri = verificationUri;
            this.intervalSeconds = intervalSeconds;
            this.expiresInSeconds = expiresInSeconds;
        }

        public String getUserCode() {
            return userCode;
        }

        public String getDeviceCode() {
            return deviceCode;
        }

        public String getVerificationUri() {
            return verificationUri;
        }

        /** Deep link that pre-fills the code on the Microsoft sign-in page. */
        public String getVerificationUriWithCode() {
            return verificationUri + (verificationUri.indexOf('?') < 0 ? "?otc=" : "&otc=") + urlEncode(userCode);
        }

        public int getIntervalSeconds() {
            return Math.max(1, intervalSeconds);
        }

        public int getExpiresInSeconds() {
            return expiresInSeconds <= 0 ? 900 : expiresInSeconds;
        }
    }
}
