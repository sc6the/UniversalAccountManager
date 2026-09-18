package me.proxycracked.universalaccountmanager.store;

import org.apache.commons.lang3.StringUtils;

/**
 * One delivered account, in whatever shape the shop sent it.
 *
 * <p>The three shops deliver three different things - Localts a refresh token or a cookie value,
 * Nicealts an {@code mctoken: ... | refreshtoken: ...} pair, Fernan Club a base64 cookie file next
 * to a live access token - so every delivery is normalised into this before the importer sees it
 * and the rest of the mod stops caring which shop it came from.</p>
 */
public final class StoreItem {
    private final String id;
    private final String content;
    private final StoreItemKind kind;
    private final String label;
    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String refreshToken;

    public StoreItem(String id, String content, StoreItemKind kind, String label,
                     String username, String uuid, String accessToken, String refreshToken) {
        this.id = id == null ? "" : id;
        this.content = content == null ? "" : content;
        this.kind = kind == null ? StoreItemKind.UNSUPPORTED : kind;
        this.label = label == null ? "" : label;
        this.username = username == null ? "" : username;
        this.uuid = uuid == null ? "" : uuid;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public static StoreItem of(String id, String content, StoreItemKind kind, String label) {
        return new StoreItem(id, content, kind, label, "", "", "", "");
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public StoreItemKind getKind() { return kind; }
    public String getLabel() { return label; }
    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }

    public StoreItem withLabel(String newLabel) {
        return new StoreItem(id, content, kind, newLabel, username, uuid, accessToken, refreshToken);
    }

    /** The name to show before the account has been logged into, when the shop supplied one. */
    public String displayName() {
        return StringUtils.isBlank(username) ? label : username;
    }

    public boolean isSupported() {
        return kind != StoreItemKind.UNSUPPORTED
            && (!StringUtils.isBlank(content) || !StringUtils.isBlank(accessToken) || !StringUtils.isBlank(refreshToken));
    }
}
