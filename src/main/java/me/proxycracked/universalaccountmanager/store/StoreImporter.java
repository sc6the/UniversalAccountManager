package me.proxycracked.universalaccountmanager.store;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.AccountTypes;
import me.proxycracked.universalaccountmanager.auth.CookieAuth;
import me.proxycracked.universalaccountmanager.auth.MsaAuth;
import me.proxycracked.universalaccountmanager.auth.RefreshTokenParser;

import net.minecraft.util.Session;
import org.apache.commons.lang3.StringUtils;

/**
 * Turns a delivered item into a logged-in account, whatever the shop handed over.
 *
 * <p>Runs on the caller's thread and must not be called from the client thread. {@code authExecutor}
 * has to be a different pool from the one the caller is on, because the cookie login is run as a
 * future on it and joined here.</p>
 */
public final class StoreImporter {
    private static final long LOGIN_TIMEOUT_SECONDS = 120L;

    private StoreImporter() {
    }

    public static AccountLogin.Result importItem(StoreItem item, Executor authExecutor) throws Exception {
        if (item == null || !item.isSupported()) {
            throw new StoreException("This delivery is not in a format the mod can log in with");
        }

        Exception firstError = null;

        // A refresh token is the only delivery that survives the day, so it always wins.
        String refreshToken = firstNonBlank(item.getRefreshToken(), refreshTokenFrom(item));
        if (!StringUtils.isBlank(refreshToken)) {
            try {
                return AccountLogin.fromRefreshToken(refreshToken, AccountTypes.MSA, null);
            } catch (Exception error) {
                firstError = error;
            }
        }

        // Cookies can be traded for a refresh token, so they come before the access token.
        if (item.getKind() == StoreItemKind.COOKIE_FILE || item.getKind() == StoreItemKind.COOKIE_TOKEN) {
            try {
                return fromCookies(item, authExecutor);
            } catch (Exception error) {
                if (firstError == null) {
                    firstError = error;
                }
            }
        }

        String accessToken = firstNonBlank(item.getAccessToken(),
            item.getKind() == StoreItemKind.ACCESS_TOKEN ? item.getContent() : "");
        if (!StringUtils.isBlank(accessToken)) {
            try {
                Session session = MsaAuth.profileSession(accessToken.trim());
                return new AccountLogin.Result(session, "", accessToken.trim(), AccountTypes.TOKEN);
            } catch (Exception error) {
                if (firstError == null) {
                    firstError = error;
                }
            }
        }

        throw firstError == null ? new StoreException("Nothing in this delivery could be logged in") : firstError;
    }

    private static String refreshTokenFrom(StoreItem item) {
        if (item.getKind() != StoreItemKind.REFRESH_TOKEN) {
            return "";
        }
        String parsed = RefreshTokenParser.parseOne(item.getContent());
        return parsed.isEmpty() ? item.getContent().trim() : parsed;
    }

    private static AccountLogin.Result fromCookies(StoreItem item, Executor authExecutor) throws Exception {
        CookieAuth.CookieResult result;
        if (item.getKind() == StoreItemKind.COOKIE_FILE) {
            String text = decodeCookieFile(item.getContent());
            if (StringUtils.isBlank(text)) {
                throw new StoreException("Delivered cookie file is empty");
            }
            result = CookieAuth.loginFromText(text, null, authExecutor).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } else {
            result = fromCookieToken(item.getContent(), authExecutor);
        }
        if (StringUtils.isBlank(result.refreshToken)) {
            return new AccountLogin.Result(result.session, "", result.accessToken, AccountTypes.COOKIE);
        }
        return new AccountLogin.Result(result.session, result.refreshToken, result.accessToken, AccountTypes.MSA);
    }

    /** Localts sells the bare MSAUTH value, so a minimal cookie file is built around it. */
    private static CookieAuth.CookieResult fromCookieToken(String content, Executor authExecutor) throws Exception {
        String parsed = RefreshTokenParser.parseOne(content);
        final String token = parsed.isEmpty() ? (content == null ? "" : content.trim()) : parsed;
        if (token.isEmpty()) {
            throw new StoreException("Purchased cookie token is empty");
        }
        File cookieFile = File.createTempFile("uam-store-cookie-", ".txt");
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(cookieFile));
            try {
                writer.println(".login.live.com\tTRUE\t/\tTRUE\t0\tMSAAUTH\t" + token);
                writer.println(".login.live.com\tTRUE\t/\tTRUE\t0\t__Host-MSAAUTH\t" + token);
                writer.println(".login.live.com\tTRUE\t/\tTRUE\t0\tMSPAuth\t" + token);
            } finally {
                writer.close();
            }
            return CookieAuth.loginFromFile(cookieFile, null, authExecutor).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            if (!cookieFile.delete()) {
                cookieFile.deleteOnExit();
            }
        }
    }

    /** Fernan Club base64-encodes the whole Netscape file; other shops may send it as-is. */
    static String decodeCookieFile(String content) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.indexOf('\t') >= 0 || value.startsWith("#") || value.startsWith(".")) {
            return value;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.replaceAll("\\s", ""));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.isBlank(first) ? (second == null ? "" : second) : first;
    }
}
