package me.proxycracked.universalaccountmanager.store;

/** What a shop actually delivered, which decides how the item is turned into an account. */
public enum StoreItemKind {
    /** A Microsoft refresh token, the only delivery that never expires. */
    REFRESH_TOKEN,
    /** A bare MSAUTH cookie value; a cookie file is built around it before logging in. */
    COOKIE_TOKEN,
    /** A whole Netscape cookies.txt (plain or base64), as Fernan Club delivers. */
    COOKIE_FILE,
    /** A ready Minecraft access token; usable immediately, dead in about a day. */
    ACCESS_TOKEN,
    UNSUPPORTED
}
