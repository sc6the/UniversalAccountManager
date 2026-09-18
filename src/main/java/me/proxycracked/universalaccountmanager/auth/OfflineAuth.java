package me.proxycracked.universalaccountmanager.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.util.Session;

/** Cracked/offline accounts: no Microsoft, no token, just the username the server will see. */
public final class OfflineAuth {
    private OfflineAuth() {
    }

    /** The same UUID a vanilla offline-mode server derives from a name. */
    public static String offlineUuid(String username) {
        String seed = "OfflinePlayer:" + username;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    /**
     * Placeholder access token. It is stored on the account so the manager can tell offline
     * entries apart and highlight the active one, and it is never sent anywhere that matters:
     * offline accounts only work on offline-mode servers.
     */
    public static String offlineToken(String username) {
        return "offline:" + offlineUuid(username);
    }

    public static Session session(String username) {
        String name = username == null ? "" : username.trim();
        return new Session(name, offlineUuid(name), offlineToken(name), Session.Type.LEGACY.toString());
    }

    public static Account account(String username) {
        String name = username == null ? "" : username.trim();
        return new Account(AccountTypes.OFFLINE, "", offlineToken(name), name, offlineUuid(name), 0L);
    }

    /** Minecraft names: 3-16 characters of {@code [A-Za-z0-9_]}. */
    public static String validate(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            return "Username is empty.";
        }
        if (name.length() < 3 || name.length() > 16) {
            return "Username must be 3-16 characters.";
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
            if (!allowed) {
                return "Only letters, digits and _ are allowed.";
            }
        }
        return null;
    }
}
