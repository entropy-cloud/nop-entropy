package io.nop.ai.toolkit.compact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SHA-256 content hasher used by reference-style compaction. Produces the
 * read-back key embedded in a {@link ShortRef} and re-computed by the
 * {@code read-ref} tool to verify that the archived content matches the
 * original.
 *
 * <p>The hash format is {@code "sha256:<hex>"} (64 lowercase hex chars prefixed
 * with the algorithm name). The algorithm prefix is part of the stored key so
 * future hash-algorithm migrations are explicit at the call site.
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.2 Decision E.
 */
public final class ShortRefHasher {

    public static final String ALGORITHM_PREFIX = "sha256:";
    public static final String ALGORITHM = "SHA-256";

    private ShortRefHasher() {
    }

    /**
     * Compute the content hash of the given string (UTF-8 encoded).
     *
     * @param content the content to hash; must not be null
     * @return the hash in {@code "sha256:<hex>"} form
     * @throws IllegalArgumentException if content is null
     */
    public static String hash(String content) {
        Objects.requireNonNull(content, "content must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return ALGORITHM_PREFIX + toHexLower(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in this JVM", e);
        }
    }

    /**
     * Re-compute the hash of {@code content} and compare it to {@code expected}.
     * Both arguments use the {@code "sha256:<hex>"} form for the hash.
     *
     * @param content  the content whose hash to recompute; must not be null
     * @param expected the expected hash; must not be null
     * @return {@code true} if the recomputed hash equals {@code expected}
     */
    public static boolean verify(String content, String expected) {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(expected, "expected hash must not be null");
        return hash(content).equals(expected);
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(HEX_LOWER[v >>> 4]).append(HEX_LOWER[v & 0x0F]);
        }
        return sb.toString();
    }

    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();
}
