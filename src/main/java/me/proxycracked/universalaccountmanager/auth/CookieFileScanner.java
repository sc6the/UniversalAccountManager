package me.proxycracked.universalaccountmanager.auth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds cookie exports lying around on disk so the login screen can list them.
 *
 * <p>Every candidate is actually parsed, so what the list shows is "this file contains N Microsoft
 * cookies", not "this file is called cookies.txt". Files that parse to nothing are dropped - they
 * could not log in anyway - and Browse... is still there for anything in an unusual place.</p>
 */
public final class CookieFileScanner {
    private static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_FILES_EXAMINED = 400;
    private static final int MAX_CANDIDATES = 40;

    private CookieFileScanner() {
    }

    public static List<Candidate> scan(File... extraRoots) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        int examined = 0;

        for (File directory : roots(extraRoots)) {
            for (File file : listCookieFiles(directory)) {
                if (examined >= MAX_FILES_EXAMINED || candidates.size() >= MAX_CANDIDATES) {
                    break;
                }
                examined++;
                Candidate candidate = examine(file);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        // Files that can actually sign in first, newest first within that.
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                if (left.hasSignInCookies() != right.hasSignInCookies()) {
                    return left.hasSignInCookies() ? -1 : 1;
                }
                return Long.compare(right.getModified(), left.getModified());
            }
        });
        return candidates;
    }

    /** Parses one file, returning null when it holds no Microsoft cookies. */
    public static Candidate examine(File file) {
        if (file == null || !file.isFile() || file.length() == 0L || file.length() > MAX_FILE_BYTES) {
            return null;
        }
        try {
            CookieJar jar = CookieJar.fromNetscapeFile(file);
            if (jar.microsoftCookieCount() == 0 && file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                // Extension exports are JSON. Null default domain: an entry has to name a Microsoft
                // domain itself, otherwise any JSON file with name/value fields would land here.
                jar = CookieJar.fromText(read(file), null);
            }
            int microsoft = jar.microsoftCookieCount();
            return microsoft == 0 ? null : new Candidate(file, microsoft, jar.hasSignInCookies());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<File> roots(File[] extraRoots) {
        Set<File> roots = new LinkedHashSet<File>();
        String home = System.getProperty("user.home");
        if (home != null) {
            roots.add(new File(home, "Downloads"));
            roots.add(new File(home, "Desktop"));
            roots.add(new File(home, "Documents"));
            roots.add(new File(home));
        }
        if (extraRoots != null) {
            for (File extra : extraRoots) {
                if (extra != null) {
                    roots.add(extra);
                    roots.add(new File(extra, "cookies"));
                }
            }
        }
        String working = System.getProperty("user.dir");
        if (working != null) {
            roots.add(new File(working));
        }

        List<File> existing = new ArrayList<File>();
        for (File root : roots) {
            if (root.isDirectory()) {
                existing.add(root);
            }
        }
        return existing;
    }

    /** Files directly in the directory plus one level of subdirectories. */
    private static List<File> listCookieFiles(File directory) {
        List<File> files = new ArrayList<File>();
        File[] entries = directory.listFiles();
        if (entries == null) {
            return files;
        }
        List<File> subdirectories = new ArrayList<File>();
        for (File entry : entries) {
            if (entry.isDirectory()) {
                subdirectories.add(entry);
            } else if (looksImportable(entry)) {
                files.add(entry);
            }
        }
        for (File subdirectory : subdirectories) {
            File[] nested = subdirectory.listFiles();
            if (nested == null) {
                continue;
            }
            for (File entry : nested) {
                if (entry.isFile() && looksImportable(entry)) {
                    files.add(entry);
                }
            }
        }
        return files;
    }

    private static boolean looksImportable(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".json");
    }

    private static String read(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        java.io.DataInputStream stream = new java.io.DataInputStream(new java.io.FileInputStream(file));
        try {
            stream.readFully(bytes);
        } finally {
            stream.close();
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static final class Candidate {
        private final File file;
        private final int microsoftCookies;
        private final boolean signInCookies;
        private final long modified;

        Candidate(File file, int microsoftCookies, boolean signInCookies) {
            this.file = file;
            this.microsoftCookies = microsoftCookies;
            this.signInCookies = signInCookies;
            this.modified = file.lastModified();
        }

        /** False when the export has Microsoft cookies but none that can authenticate. */
        public boolean hasSignInCookies() {
            return signInCookies;
        }

        public File getFile() {
            return file;
        }

        public int getMicrosoftCookies() {
            return microsoftCookies;
        }

        public long getModified() {
            return modified;
        }

        public String getName() {
            return file.getName();
        }

        public String getDirectory() {
            File parent = file.getParentFile();
            return parent == null ? "" : parent.getAbsolutePath();
        }
    }
}
