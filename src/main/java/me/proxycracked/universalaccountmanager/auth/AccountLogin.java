package me.proxycracked.universalaccountmanager.auth;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import me.proxycracked.universalaccountmanager.UniversalAccountManager;

import org.apache.commons.lang3.StringUtils;

import net.minecraft.util.Session;

/**
 * One login path for every account type in the manager.
 *
 * <p>The bundled jar decided how to log in from the account type alone, and only ever refreshed
 * through the Azure app, so a stored refresh token minted by the legacy MSA client (alt-shop
 * imports, cookie logins, device-code logins) could never be redeemed: as soon as the 24h
 * Minecraft token expired the account looked dead. This tries the stored access token first, then
 * both Microsoft clients, and records which one worked so the next login goes straight there.</p>
 */
public final class AccountLogin {
    /** Runs each auth step on the calling thread; the callers are already off the client thread. */
    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private AccountLogin() {
    }

    public static Result login(Account account, Consumer<String> status) throws Exception {
        if (account == null) {
            throw new Exception("No account selected");
        }

        if (AccountTypes.isOffline(account)) {
            report(status, "&7Switching to offline account...&r");
            Session session = OfflineAuth.session(account.getUsername());
            return new Result(session, "", session.getToken(), AccountTypes.OFFLINE);
        }

        String accessToken = account.getAccessToken();
        if (!StringUtils.isBlank(accessToken)) {
            report(status, "&7Checking stored token...&r");
            try {
                Session session = MsaAuth.profileSession(accessToken);
                return new Result(session, account.getRefreshToken(), accessToken, account.getType());
            } catch (Exception expired) {
                if (!AccountTypes.isRefreshable(account)) {
                    throw new Exception("Token expired and this account has no refresh token - re-add it");
                }
            }
        }

        if (!AccountTypes.isRefreshable(account)) {
            throw new Exception("This account has no usable token, re-add it");
        }

        return fromRefreshToken(account.getRefreshToken(), account.getType(), status);
    }

    /**
     * Redeems a refresh token without knowing which client minted it.
     *
     * <p>Alt shops and the device-code flow hand out legacy MSA tokens, the in-mod browser login
     * hands out Azure ones, and the two are not interchangeable, so whichever one the account is
     * tagged with is tried first and the other is the fallback.</p>
     */
    public static Result fromRefreshToken(String refreshToken, String preferredType, Consumer<String> status) throws Exception {
        String token = refreshToken == null ? "" : refreshToken.trim();
        if (token.isEmpty()) {
            throw new Exception("Refresh token is empty");
        }

        boolean azureFirst = AccountTypes.MS.equals(preferredType);
        Exception firstError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean azure = azureFirst == (attempt == 0);
            try {
                return azure ? withAzureClient(token, status) : withMsaClient(token, status);
            } catch (Exception error) {
                if (firstError == null) {
                    firstError = error;
                }
            }
        }
        throw firstError;
    }

    private static Result withMsaClient(String refreshToken, Consumer<String> status) throws Exception {
        report(status, "&7Refreshing Microsoft login...&r");
        MsaAuth.TokenPair tokens = MsaAuth.refresh(refreshToken);
        report(status, "&7Signing in to Xbox Live...&r");
        Session session = MsaAuth.minecraftLogin(tokens.getAccessToken());
        return new Result(session, tokens.getRefreshToken(), session.getToken(), AccountTypes.MSA);
    }

    private static Result withAzureClient(String refreshToken, Consumer<String> status) throws Exception {
        try {
            report(status, "&7Refreshing Microsoft login...&r");
            Map<String, String> tokens = MicrosoftAuth.refreshMSAccessTokens(refreshToken, DIRECT).join();
            report(status, "&7Signing in to Xbox Live...&r");
            String xbox = MicrosoftAuth.acquireXboxAccessToken(tokens.get("access_token"), DIRECT).join();
            Map<String, String> xsts = MicrosoftAuth.acquireXboxXstsToken(xbox, DIRECT).join();
            report(status, "&7Acquiring Minecraft token...&r");
            String minecraftToken = MicrosoftAuth.acquireMCAccessToken(xsts.get("Token"), xsts.get("uhs"), DIRECT).join();
            report(status, "&7Fetching profile...&r");
            Session session = MicrosoftAuth.login(minecraftToken, DIRECT).join();
            String rotated = tokens.get("refresh_token");
            return new Result(session, StringUtils.isBlank(rotated) ? refreshToken : rotated, minecraftToken, AccountTypes.MS);
        } catch (CompletionException error) {
            throw new Exception(AuthHttp.rootMessage(error));
        }
    }

    /** Copies a finished login onto the stored account and persists it. */
    public static void apply(Account account, Result result) {
        if (account == null || result == null) {
            return;
        }
        synchronized (UniversalAccountManager.accounts) {
            account.setType(result.getType());
            if (!StringUtils.isBlank(result.getRefreshToken())) {
                account.setRefreshToken(result.getRefreshToken());
            }
            account.setAccessToken(result.getAccessToken());
            account.setUsername(result.getSession().getUsername());
            if (!StringUtils.isBlank(result.getSession().getPlayerID())) {
                account.setUuid(result.getSession().getPlayerID());
            }
            account.setAvailable(Boolean.TRUE);
        }
        UniversalAccountManager.save();
    }

    /** Finds the stored account for a login, creating it when the profile is new. */
    public static Account upsert(Result result) {
        Session session = result.getSession();
        Account match = find(session.getPlayerID(), session.getUsername());
        if (match == null) {
            match = new Account(
                result.getType(),
                result.getRefreshToken(),
                result.getAccessToken(),
                session.getUsername(),
                session.getPlayerID(),
                0L
            );
            match.setAvailable(Boolean.TRUE);
            synchronized (UniversalAccountManager.accounts) {
                UniversalAccountManager.accounts.add(match);
            }
            UniversalAccountManager.resort();
            UniversalAccountManager.save();
            return match;
        }
        apply(match, result);
        return match;
    }

    public static Account find(String uuid, String username) {
        synchronized (UniversalAccountManager.accounts) {
            for (Account account : UniversalAccountManager.accounts) {
                boolean uuidMatch = !StringUtils.isBlank(uuid)
                    && !StringUtils.isBlank(account.getUuid())
                    && uuid.equalsIgnoreCase(account.getUuid());
                boolean nameMatch = !StringUtils.isBlank(username)
                    && username.equalsIgnoreCase(account.getUsername());
                if (uuidMatch || nameMatch) {
                    return account;
                }
            }
        }
        return null;
    }

    private static void report(Consumer<String> status, String message) {
        if (status != null) {
            status.accept(message);
        }
    }

    public static final class Result {
        private final Session session;
        private final String refreshToken;
        private final String accessToken;
        private final String type;

        public Result(Session session, String refreshToken, String accessToken, String type) {
            this.session = session;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
            this.accessToken = accessToken == null ? "" : accessToken;
            this.type = type == null ? AccountTypes.MSA : type;
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

        public String getType() {
            return type;
        }
    }
}
