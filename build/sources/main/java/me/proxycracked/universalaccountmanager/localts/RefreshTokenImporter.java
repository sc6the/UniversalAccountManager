package me.proxycracked.universalaccountmanager.localts;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import me.proxycracked.universalaccountmanager.auth.MicrosoftAuth;
import net.minecraft.util.Session;

public final class RefreshTokenImporter {
    private RefreshTokenImporter() {
    }

    public static CompletableFuture<ImportedAccount> importToken(String refreshToken, Executor executor) {
        AtomicReference<String> rotatedRefreshToken = new AtomicReference<>(refreshToken);
        AtomicReference<String> minecraftAccessToken = new AtomicReference<>("");

        return MicrosoftAuth.refreshMSAccessTokens(refreshToken, executor)
            .thenComposeAsync(tokens -> {
                rotatedRefreshToken.set(tokens.get("refresh_token"));
                return MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), executor);
            }, executor)
            .thenComposeAsync(xboxToken -> MicrosoftAuth.acquireXboxXstsToken(xboxToken, executor), executor)
            .thenComposeAsync(xsts -> MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), executor), executor)
            .thenComposeAsync(accessToken -> {
                minecraftAccessToken.set(accessToken);
                return MicrosoftAuth.login(accessToken, executor);
            }, executor)
            .thenApply(session -> new ImportedAccount(session, rotatedRefreshToken.get(), minecraftAccessToken.get()));
    }

    public static final class ImportedAccount {
        private final Session session;
        private final String refreshToken;
        private final String accessToken;

        private ImportedAccount(Session session, String refreshToken, String accessToken) {
            this.session = session;
            this.refreshToken = refreshToken;
            this.accessToken = accessToken;
        }

        public Session getSession() {
            return session;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getAccessToken() {
            return accessToken;
        }
    }
}
