package me.proxycracked.universalaccountmanager.localts;

import me.proxycracked.universalaccountmanager.auth.AccountLogin;
import me.proxycracked.universalaccountmanager.auth.MsaAuth;

import net.minecraft.util.Session;

/**
 * Microsoft login for refresh tokens sold by Localts.
 *
 * <p>Those tokens are minted by the legacy MSA client, so they cannot be redeemed through the Azure
 * application the bundled {@code MicrosoftAuth} uses. The chain that does redeem them now lives in
 * {@link MsaAuth} because the device-code and cookie logins need exactly the same steps; this class
 * stays as the entry point the store screens already call.</p>
 */
public final class LocaltsRefreshTokenAuth {
    private LocaltsRefreshTokenAuth() {
    }

    public static Result login(String refreshToken) throws Exception {
        MsaAuth.TokenPair tokens = MsaAuth.refresh(refreshToken);
        Session session = MsaAuth.minecraftLogin(tokens.getAccessToken());
        return new Result(session, tokens.getRefreshToken(), session.getToken());
    }

    /** Same, but falls back to the Azure client for tokens that did not come from a shop. */
    public static Result loginWithFallback(String refreshToken) throws Exception {
        AccountLogin.Result result = AccountLogin.fromRefreshToken(refreshToken, null, null);
        return new Result(result.getSession(), result.getRefreshToken(), result.getAccessToken());
    }

    public static final class Result {
        private final Session session;
        private final String refreshToken;
        private final String accessToken;

        Result(Session session, String refreshToken, String accessToken) {
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
