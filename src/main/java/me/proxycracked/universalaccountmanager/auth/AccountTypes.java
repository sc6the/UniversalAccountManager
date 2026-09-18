package me.proxycracked.universalaccountmanager.auth;

import org.apache.commons.lang3.StringUtils;

/**
 * The {@code type} strings stored in {@code universalaccountmanager_accounts.json}.
 *
 * <p>The bundled jar knows {@code ms}, {@code token}, {@code cookie} and {@code launcher}. Two more
 * are added here: {@code msa} for accounts whose refresh token belongs to the legacy MSA client
 * (device-code logins, cookie logins and every alt-shop token), and {@code offline} for accounts
 * with no Microsoft side at all.</p>
 */
public final class AccountTypes {
    public static final String MS = "ms";
    public static final String MSA = "msa";
    public static final String TOKEN = "token";
    public static final String COOKIE = "cookie";
    public static final String LAUNCHER = "launcher";
    public static final String OFFLINE = "offline";

    private AccountTypes() {
    }

    public static boolean isOffline(Account account) {
        return account != null && OFFLINE.equals(account.getType());
    }

    public static boolean isMsa(Account account) {
        return account != null && MSA.equals(account.getType());
    }

    /** True when the account can be brought back to life without the user pasting anything. */
    public static boolean isRefreshable(Account account) {
        return account != null && !StringUtils.isBlank(account.getRefreshToken());
    }

    public static String badge(Account account) {
        if (account == null) {
            return "&8[&7?&8]&r";
        }
        String type = account.getType();
        if (OFFLINE.equals(type)) {
            return "&8[&7OFFLINE&8]&r";
        }
        if (MSA.equals(type)) {
            return "&8[&aMSA&8]&r";
        }
        if (LAUNCHER.equals(type)) {
            return "&8[&fLAUNCHER&8]&r";
        }
        if (TOKEN.equals(type)) {
            return "&8[&dTOKEN&8]&r";
        }
        if (COOKIE.equals(type)) {
            return "&8[&eCOOKIE&8]&r";
        }
        return "&8[&bMS&8]&r";
    }
}
