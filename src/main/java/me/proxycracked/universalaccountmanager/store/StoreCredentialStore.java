package me.proxycracked.universalaccountmanager.store;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;

import com.sun.jna.platform.win32.Crypt32Util;

import net.minecraft.client.Minecraft;

/**
 * Stores each shop's API key encrypted for the current Windows user with DPAPI.
 *
 * <p>One class for every shop so a key is saved, loaded and forgotten the same way everywhere. The
 * file names are the ones the per-shop stores already used, so existing keys keep working.</p>
 */
public final class StoreCredentialStore {
    private StoreCredentialStore() {
    }

    public static String load(String providerId) throws IOException {
        File file = credentialFile(providerId);
        if (!file.isFile()) {
            return importedFromManagerMod(providerId);
        }
        byte[] encrypted = Base64.getDecoder().decode(
            new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim()
        );
        byte[] clear = Crypt32Util.cryptUnprotectData(encrypted);
        try {
            return new String(clear, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    public static void save(String providerId, String apiKey) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is empty");
        }
        File file = credentialFile(providerId);
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the config directory");
        }

        byte[] clear = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try {
            encrypted = Crypt32Util.cryptProtectData(clear);
        } finally {
            Arrays.fill(clear, (byte) 0);
        }

        File temporary = new File(directory, file.getName() + ".tmp");
        Files.write(temporary.toPath(), Base64.getEncoder().encode(encrypted));
        Arrays.fill(encrypted, (byte) 0);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void forget(String providerId) {
        File file = credentialFile(providerId);
        if (file.isFile() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    /**
     * Nicealts' own manager mod keeps its keys as plain text under {@code %APPDATA%/nicealts_manager}.
     * Reading them the first time saves re-pasting a key that is already on this machine; it is
     * re-saved through DPAPI as soon as it connects.
     */
    private static String importedFromManagerMod(String providerId) {
        String fileName;
        if ("nicealts".equals(providerId)) {
            fileName = "nicealtsapi.txt";
        } else if ("localts".equals(providerId)) {
            fileName = "localtsapi.txt";
        } else {
            return "";
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            return "";
        }
        File file = new File(new File(appData, "nicealts_manager"), fileName);
        if (!file.isFile()) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static File credentialFile(String providerId) {
        String name = "universalaccountmanager_" + providerId + "_api_key.dat";
        return new File(Minecraft.getMinecraft().mcDataDir, "config/" + name);
    }
}
