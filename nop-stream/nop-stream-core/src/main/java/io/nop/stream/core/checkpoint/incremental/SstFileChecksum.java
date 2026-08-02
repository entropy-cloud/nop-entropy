/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Content-addressing helper: computes the SHA-256 digest of an SST (or any state)
 * file as a lowercase hex string. Two files with identical content yield the same
 * digest, which is what {@link SharedStateRegistry} keys on for cross-checkpoint
 * de-duplication.
 */
public final class SstFileChecksum {

    public static final String ALGORITHM = "SHA-256";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SstFileChecksum() {
    }

    /** Compute the SHA-256 hex digest of a file on disk (streamed, constant memory). */
    public static String sha256Hex(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        return toHex(digest.digest());
    }

    /** Compute the SHA-256 hex digest of an in-memory byte array. */
    public static String sha256Hex(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        MessageDigest digest = newDigest();
        digest.update(content);
        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec; this is genuinely unreachable.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }
}
