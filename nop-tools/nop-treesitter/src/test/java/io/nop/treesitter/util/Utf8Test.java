package io.nop.treesitter.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Utf8Test {

    private static int[] decode(String s, int offset) {
        return Utf8.decodeCodepoint(s.getBytes(StandardCharsets.UTF_8), offset);
    }

    @Test
    void decodesAsciiTwoThreeAndFourByteSequences() {
        assertArrayEquals(new int[]{'a', 1}, decode("a", 0));
        // U+00E9 é — 2 bytes
        assertArrayEquals(new int[]{0xE9, 2}, decode("\u00E9", 0));
        // U+20AC € — 3 bytes
        assertArrayEquals(new int[]{0x20AC, 3}, decode("\u20AC", 0));
        // U+1F600 — 4 bytes
        assertArrayEquals(new int[]{0x1F600, 4}, decode("\uD83D\uDE00", 0));
    }

    @Test
    void decodesAtInteriorOffsets() {
        byte[] bytes = "a\u00E9b".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(new int[]{'a', 1}, Utf8.decodeCodepoint(bytes, 0));
        assertArrayEquals(new int[]{0xE9, 2}, Utf8.decodeCodepoint(bytes, 1));
        assertArrayEquals(new int[]{'b', 1}, Utf8.decodeCodepoint(bytes, 3));
    }

    @Test
    void returnsNullPastEnd() {
        assertNull(Utf8.decodeCodepoint(new byte[0], 0));
        assertNull(Utf8.decodeCodepoint("ab".getBytes(StandardCharsets.UTF_8), 2));
    }

    @Test
    void truncatedSequencesDegradeToSingleByte() {
        // leading byte of a 3-byte sequence with no continuation bytes
        int[] dec = Utf8.decodeCodepoint(new byte[]{(byte) 0xE2}, 0);
        assertArrayEquals(new int[]{0xE2, 1}, dec);
        // 2-byte leading byte followed by a non-continuation byte
        int[] dec2 = Utf8.decodeCodepoint(new byte[]{(byte) 0xC3, 'x'}, 0);
        assertArrayEquals(new int[]{0xC3, 1}, dec2);
    }

    @Test
    void invalidLeadBytesDegradeToSingleByte() {
        int[] dec = Utf8.decodeCodepoint(new byte[]{(byte) 0xFF}, 0);
        assertArrayEquals(new int[]{0xFF, 1}, dec);
    }
}
