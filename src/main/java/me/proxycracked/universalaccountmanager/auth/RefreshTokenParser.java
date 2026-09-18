package me.proxycracked.universalaccountmanager.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pulls refresh tokens out of whatever the shops hand over.
 *
 * <p>Deliveries come as a bare token, as {@code mail:password:token}, as {@code mail:token}, or as a
 * whole file of those lines, so each line is reduced to the segment that actually looks like a
 * token rather than being split on the first colon.</p>
 */
public final class RefreshTokenParser {
    private static final int MIN_TOKEN_LENGTH = 20;

    private RefreshTokenParser() {
    }

    /** Every distinct token in a blob of pasted or file-loaded text, in the order they appear. */
    public static List<String> parseAll(String blob) {
        Set<String> tokens = new LinkedHashSet<String>();
        if (blob != null) {
            for (String line : blob.split("[\\r\\n]+")) {
                for (String field : line.split("[\\s,;]+")) {
                    String token = parseOne(field);
                    if (!token.isEmpty()) {
                        tokens.add(token);
                    }
                }
            }
        }
        return new ArrayList<String>(tokens);
    }

    /** The token inside a single {@code mail:password:token}-style field, or "" if there is none. */
    public static String parseOne(String field) {
        String value = strip(field);
        if (value.isEmpty()) {
            return "";
        }
        if (value.indexOf(':') < 0) {
            return value.length() >= MIN_TOKEN_LENGTH ? value : "";
        }

        String[] segments = value.split(":");
        String best = "";
        for (String segment : segments) {
            String candidate = strip(segment);
            // Legacy MSA refresh tokens all start with "M." and are by far the longest field.
            if (candidate.startsWith("M.") && candidate.length() > best.length()) {
                best = candidate;
            }
        }
        if (best.isEmpty()) {
            for (String segment : segments) {
                String candidate = strip(segment);
                if (candidate.length() > best.length()) {
                    best = candidate;
                }
            }
        }
        return best.length() >= MIN_TOKEN_LENGTH ? best : "";
    }

    private static String strip(String value) {
        String stripped = value == null ? "" : value.trim();
        while (stripped.length() > 1 && (stripped.startsWith("\"") || stripped.startsWith("'"))) {
            stripped = stripped.substring(1).trim();
        }
        while (stripped.length() > 1 && (stripped.endsWith("\"") || stripped.endsWith("'") || stripped.endsWith(","))) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }
}
