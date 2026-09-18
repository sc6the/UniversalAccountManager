package me.proxycracked.universalaccountmanager.localts;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import me.proxycracked.universalaccountmanager.auth.AccountLogin;

import net.minecraft.util.Session;

/** Redeems a refresh token off the client thread, whichever Microsoft client minted it. */
public final class RefreshTokenImporter {
    private RefreshTokenImporter() {
    }

    public static CompletableFuture<ImportedAccount> importToken(final String refreshToken, Executor executor) {
        return importToken(refreshToken, null, executor);
    }

    /**
     * @param preferredType the account's stored type, so the client that minted the token is tried
     *                      first; the other one is still used as a fallback
     */
    public static CompletableFuture<ImportedAccount> importToken(final String refreshToken, final String preferredType, Executor executor) {
        return CompletableFuture.supplyAsync(new Supplier<ImportedAccount>() {
            @Override
            public ImportedAccount get() {
                try {
                    AccountLogin.Result result = AccountLogin.fromRefreshToken(refreshToken, preferredType, null);
                    return new ImportedAccount(result);
                } catch (Exception error) {
                    throw new CompletionException(error);
                }
            }
        }, executor);
    }

    public static final class ImportedAccount {
        private final AccountLogin.Result result;

        ImportedAccount(AccountLogin.Result result) {
            this.result = result;
        }

        public Session getSession() {
            return result.getSession();
        }

        public String getRefreshToken() {
            return result.getRefreshToken();
        }

        public String getAccessToken() {
            return result.getAccessToken();
        }

        /** {@code msa} or {@code ms}, depending on which client accepted the token. */
        public String getType() {
            return result.getType();
        }

        public AccountLogin.Result getResult() {
            return result;
        }
    }
}
