package me.proxycracked.universalaccountmanager.localts;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import net.minecraft.client.Minecraft;

/** Stores the Localts API key encrypted for the current Windows user with DPAPI. */
public final class LocalTsCredentialStore {
    private LocalTsCredentialStore() {
    }

    public static String load() throws IOException {
        File file = credentialFile();
        if (!file.isFile()) return "";

        byte[] protectedBytes = Base64.getDecoder().decode(
            new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).trim()
        );
        byte[] clearBytes = Crypt32Util.cryptUnprotectData(protectedBytes);
        try {
            return new String(clearBytes, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(clearBytes, (byte) 0);
        }
    }

    public static void save(String apiKey) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key is empty");
        }

        File file = credentialFile();
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the config directory");
        }

        byte[] clearBytes = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] protectedBytes;
        try {
            protectedBytes = Crypt32Util.cryptProtectData(clearBytes);
        } finally {
            Arrays.fill(clearBytes, (byte) 0);
        }

        File temporary = new File(directory, file.getName() + ".tmp");
        Files.write(temporary.toPath(), Base64.getEncoder().encode(protectedBytes));
        Arrays.fill(protectedBytes, (byte) 0);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static File credentialFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/universalaccountmanager_localts_api_key.dat");
    }
}
