package io.nop.treesitter.util;

/**
 * Shared UTF-8 decoding for the lexer and scanner paths.
 *
 * <p>{@link #decodeCodepoint} returns {@code null} past the end of input, else
 * {@code {codepoint, width}}; invalid sequences degrade to a single byte value
 * (like the C runtime's {@code TS_DECODE_ERROR} handling). Callers on the
 * hottest scan loop may prefer a packed inline decode (see
 * {@code Lexer.decodePacked}); this class is the canonical, allocation-tolerant
 * variant shared by all non-packed call sites.</p>
 */
public final class Utf8 {

    private Utf8() {
    }

    /**
     * Decodes the UTF-8 codepoint at {@code p}; returns {@code null} past the end
     * of input, else {@code {codepoint, width}}.
     */
    public static int[] decodeCodepoint(byte[] source, int p) {
        int len = source.length;
        if (p >= len) {
            return null;
        }
        int b0 = source[p] & 0xFF;
        if (b0 < 0x80) {
            return new int[]{b0, 1};
        }
        if ((b0 & 0xE0) == 0xC0 && p + 1 < len) {
            int b1 = source[p + 1] & 0xFF;
            if ((b1 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x1F) << 6) | (b1 & 0x3F), 2};
            }
            return new int[]{b0, 1};
        }
        if ((b0 & 0xF0) == 0xE0 && p + 2 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F), 3};
            }
            return new int[]{b0, 1};
        }
        if ((b0 & 0xF8) == 0xF0 && p + 3 < len) {
            int b1 = source[p + 1] & 0xFF;
            int b2 = source[p + 2] & 0xFF;
            int b3 = source[p + 3] & 0xFF;
            if ((b1 & 0xC0) == 0x80 && (b2 & 0xC0) == 0x80 && (b3 & 0xC0) == 0x80) {
                return new int[]{((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12)
                        | ((b2 & 0x3F) << 6) | (b3 & 0x3F), 4};
            }
            return new int[]{b0, 1};
        }
        return new int[]{b0, 1};
    }
}
