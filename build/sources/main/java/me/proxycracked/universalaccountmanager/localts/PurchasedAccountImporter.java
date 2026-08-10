package me.proxycracked.universalaccountmanager.localts;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import me.proxycracked.universalaccountmanager.auth.CookieAuth;
import net.minecraft.util.Session;

public final class PurchasedAccountImporter {
    private PurchasedAccountImporter() {
    }

    public static CompletableFuture<ImportedAccount> importRefreshToken(String content, Executor executor) {
        String refreshToken = extractRefreshToken(content);
        return RefreshTokenImporter.importToken(refreshToken, executor)
            .thenApply(account -> new ImportedAccount(
                "ms", account.getSession(), account.getRefreshToken(), account.getAccessToken()
            ));
    }

    public static CompletableFuture<ImportedAccount> importCookie(String msAuth, Executor executor) throws Exception {
        final String token = msAuth == null ? "" : msAuth.trim();
        if (token.isEmpty()) {
            throw new Exception("Purchased cookie token is empty");
        }
        final File cookieFile = File.createTempFile("uam-localts-cookie-", ".txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(cookieFile))) {
            writer.println(".login.live.com\tTRUE\t/\tTRUE\t0\tMSAAUTH\t" + token);
            writer.println(".login.live.com\tTRUE\t/\tTRUE\t0\t__Host-MSAAUTH\t" + token);
            writer.println(".login.live.com\tTRUE\t/\tTRUE\t0\tMSPAuth\t" + token);
        }
        return CookieAuth.loginFromFile(cookieFile, ignored -> { }, executor)
            .thenApply(result -> new ImportedAccount("cookie", result.session, token, result.accessToken))
            .whenComplete((result, error) -> {
                if (!cookieFile.delete()) cookieFile.deleteOnExit();
            });
    }

    private static String extractRefreshToken(String content) {
        String value = content == null ? "" : content.trim();
        int separator = value.indexOf(':');
        if (separator >= 0 && separator + 1 < value.length()) {
            value = value.substring(separator + 1).trim();
        }
        return value;
    }

    public static final class ImportedAccount {
        private final String type;
        private final Session session;
        private final String refreshToken;
        private final String accessToken;

        private ImportedAccount(String type, Session session, String refreshToken, String accessToken) {
            this.type = type;
            this.session = session;
            this.refreshToken = refreshToken;
            this.accessToken = accessToken;
        }

        public String getType() { return type; }
        public Session getSession() { return session; }
        public String getRefreshToken() { return refreshToken; }
        public String getAccessToken() { return accessToken; }
    }
}
