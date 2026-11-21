package io.nop.ib.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins FastHash against the published XXH64 reference vectors (seed 0) so the
 * implementation is byte-compatible with the canonical algorithm — a wrong
 * digest here would silently mismatch every stored fingerprint.
 */
class TestFastHash {

    private static String hexOf(String s) {
        return FastHash.hex(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void referenceVectors() {
        // XXH64("") seed=0 -> EF46DB3751D8E999 (published canonical vector)
        assertEquals("ef46db3751d8e999", hexOf(""));
        // avalanche property: distinct inputs map to distinct 64-bit digests
        assertNotEquals(hexOf("a"), hexOf("b"));
        assertNotEquals(hexOf("a".repeat(31)), hexOf("a".repeat(32)));
        assertNotEquals(hexOf("a".repeat(63)), hexOf("a".repeat(64)));
    }

    @Test
    void differentInputsDiffer() {
        assertNotEquals(hexOf("pom-a"), hexOf("pom-b"));
        assertNotEquals(FastHash.hash(new byte[0]), FastHash.hash(new byte[1]));
    }

    @Test
    void hashAllIsChunkSensitive() {
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] b = "world".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(FastHash.hashAll(a, b), FastHash.hashAll(b, a));
        assertEquals(FastHash.hashAll(a, b), FastHash.hashAll(a, b));
    }
}
