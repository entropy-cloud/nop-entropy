package io.nop.ib.tasks;

import io.nop.ib.NopIbException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Module-relative glob matching for task inputs/outputs (design 08 §二).
 * Supports {@code *} (within a segment), {@code ?} (one char) and
 * {@code **} (across directory segments). Paths use '/' separators.
 *
 * <p>Expansion granularity (design 08 / wave 12): outputs expand to matching
 * <em>files</em> only — directories never enter the fingerprint set, so the
 * fingerprint state is a file collection.
 */
public final class GlobMatcher {
    private GlobMatcher() {
    }

    /** Compiles and validates eagerly; a bad glob is a config error (fail-loud). */
    public static Pattern compile(String glob) {
        // regex metacharacters have no glob meaning — reject them instead of
        // silently literalizing (fail-loud per design 08 §二)
        for (int k = 0; k < glob.length(); k++) {
            char c = glob.charAt(k);
            if ("[]{}()^+|\\".indexOf(c) >= 0)
                throw new NopIbException("invalid glob '" + glob + "': character '" + c
                        + "' has no glob meaning");
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = glob.length();
        while (i < n) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < n && glob.charAt(i + 1) == '*') {
                    // '**' crosses segments; swallow a following '/' so "a/**" also
                    // matches "a" itself being a directory boundary
                    if (i + 2 < n && glob.charAt(i + 2) == '/')
                        i += 3;
                    else
                        i += 2;
                    sb.append(".*");
                } else {
                    i++;
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                i++;
                sb.append("[^/]");
            } else {
                if (!Character.isLetterOrDigit(c) && "_./-".indexOf(c) < 0)
                    sb.append('\\');
                sb.append(c);
                i++;
            }
        }
        try {
            return Pattern.compile(sb.toString());
        } catch (PatternSyntaxException e) {
            throw new NopIbException("invalid glob '" + glob + "': " + e.getMessage());
        }
    }

    public static boolean matches(Pattern compiled, String relPath) {
        String normalized = relPath.replace('\\', '/');
        return compiled.matcher(normalized).matches();
    }
}
