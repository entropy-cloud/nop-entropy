package io.nop.ib.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * XXH64 one-shot hashing for ib's change-detection fingerprints (poms, dumps,
 * class content, task inputs). These hashes only ever compare ib against its
 * own stored state — they are NOT integrity digests, so the cryptographic
 * overhead of SHA-256 buys nothing. XXH64 runs at multiple GB/s per core
 * (roughly an order of magnitude faster than SHA-256 on small buffered reads),
 * which matters on the 1/10-performance hosts the service must stay usable on.
 *
 * <p>Hex output is 16 chars (64-bit), distinguishable from the legacy SHA-256
 * entries (64 chars) still present in models/indexes written by older builds;
 * a mixed comparison simply reports staleness once and the next refresh
 * rewrites every stored hash.
 */
public final class FastHash {

    private static final long P1 = 0x9E3779B185EBCA87L;
    private static final long P2 = 0xC2B2AE3D27D4EB4FL;
    private static final long P3 = 0x165667B19E3779F9L;
    private static final long P4 = 0x85EBCA77C2B2AE63L;
    private static final long P5 = 0x27D4EB2F165667C5L;

    private FastHash() {
    }

    public static long hash(byte[] data) {
        return hash(data, 0, data.length, 0L);
    }

    public static long hash(byte[] data, int off, int len, long seed) {
        int end = off + len;
        long h;
        int i = off;
        if (len >= 32) {
            long v1 = seed + P1 + P2;
            long v2 = seed + P2;
            long v3 = seed;
            long v4 = seed - P1;
            int limit = end - 32;
            do {
                v1 = round(v1, getLong(data, i));
                v2 = round(v2, getLong(data, i + 8));
                v3 = round(v3, getLong(data, i + 16));
                v4 = round(v4, getLong(data, i + 24));
                i += 32;
            } while (i <= limit);
            h = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7)
                    + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
            h = mergeRound(h, v1);
            h = mergeRound(h, v2);
            h = mergeRound(h, v3);
            h = mergeRound(h, v4);
        } else {
            h = seed + P5;
        }
        h += len;
        while (i + 8 <= end) {
            long k = round(0, getLong(data, i));
            h ^= k;
            h = Long.rotateLeft(h, 27) * P1 + P4;
            i += 8;
        }
        if (i + 4 <= end) {
            h ^= (data[i] & 0xFFL) | ((data[i + 1] & 0xFFL) << 8)
                    | ((data[i + 2] & 0xFFL) << 16) | ((data[i + 3] & 0xFFL) << 24);
            h = Long.rotateLeft(h, 23) * P2 + P3;
            i += 4;
        }
        while (i < end) {
            h ^= (data[i] & 0xFFL) * P5;
            h = Long.rotateLeft(h, 11) * P1;
            i++;
        }
        h ^= h >>> 33;
        h *= P2;
        h ^= h >>> 29;
        h *= P3;
        h ^= h >>> 32;
        return h;
    }

    /** Update-style hash over a sequence of byte arrays (length-delimited). */
    public static long hashAll(byte[]... chunks) {
        int total = 0;
        for (byte[] c : chunks)
            total += c.length + 4;
        byte[] buf = new byte[total];
        int p = 0;
        for (byte[] c : chunks) {
            int n = c.length;
            buf[p++] = (byte) n;
            buf[p++] = (byte) (n >>> 8);
            buf[p++] = (byte) (n >>> 16);
            buf[p++] = (byte) (n >>> 24);
            System.arraycopy(c, 0, buf, p, n);
            p += n;
        }
        return hash(buf);
    }

    public static String hex(byte[] data) {
        return String.format("%016x", hash(data));
    }

    public static String hexOf(Path file) {
        try {
            return hex(Files.readAllBytes(file));
        } catch (java.io.IOException e) {
            throw new io.nop.ib.NopIbException("fastHash failed for " + file, e);
        }
    }

    private static long round(long acc, long input) {
        acc += input * P2;
        acc = Long.rotateLeft(acc, 31);
        acc *= P1;
        return acc;
    }

    private static long mergeRound(long h, long v) {
        h ^= round(0, v);
        return h * P1 + P4;
    }

    private static long getLong(byte[] data, int i) {
        return (data[i] & 0xFFL) | ((data[i + 1] & 0xFFL) << 8)
                | ((data[i + 2] & 0xFFL) << 16) | ((data[i + 3] & 0xFFL) << 24)
                | ((data[i + 4] & 0xFFL) << 32) | ((data[i + 5] & 0xFFL) << 40)
                | ((data[i + 6] & 0xFFL) << 48) | ((long) data[i + 7] << 56);
    }

    /** bytes of a string in UTF-8, hashed (common case for string fingerprints). */
    public static String hexOfString(String s) {
        return hex(s.getBytes(StandardCharsets.UTF_8));
    }
}
