package io.nop.lint.core.node;

/**
 * Converts the facade's UTF-8 byte offsets into the 0-based line and
 * 0-based UTF-16 column positions the L2 wire contract uses (design 06
 * §5.3: TypeScript's internal convention). Cold-path by design — type
 * queries are per-match, not per-node — so a straightforward scan is the
 * right trade against index maintenance.
 */
public final class SourcePositions {

    private SourcePositions() {
    }

    /**
     * The 0-based line and 0-based UTF-16 column of {@code byteOffset} in
     * {@code source}. A negative offset is rejected; an offset past the end
     * resolves to the last position (the caller validates against node
     * ranges first, so this only guards malformed backend data).
     */
    public static LineCol lineColUtf16(byte[] source, int byteOffset) {
        if (byteOffset < 0)
            throw new IllegalArgumentException("byteOffset must not be negative: " + byteOffset);
        int limit = Math.min(byteOffset, source.length);
        int line = 0;
        int colUtf16 = 0;
        int i = 0;
        while (i < limit) {
            byte b = source[i];
            if (b == '\n') {
                line++;
                colUtf16 = 0;
                i++;
                continue;
            }
            if (b >= 0) {
                // ASCII: one UTF-16 unit
                colUtf16++;
                i++;
                continue;
            }
            int sequenceLength = utf8SequenceLength(b);
            if (i + sequenceLength > limit) {
                // The offset splits a multi-byte sequence: treat the
                // sequence as consumed whole rather than counting a partial
                // code point.
                break;
            }
            colUtf16 += utf16Units(b);
            i += sequenceLength;
        }
        return new LineCol(line, colUtf16);
    }

    private static int utf8SequenceLength(byte lead) {
        if ((lead & (byte) 0xE0) == (byte) 0xC0)
            return 2;
        if ((lead & (byte) 0xF0) == (byte) 0xE0)
            return 3;
        return 4;
    }

    private static int utf16Units(byte lead) {
        // 4-byte sequences decode to a surrogate pair (2 UTF-16 units);
        // every other sequence is one BMP code point (1 unit).
        return (lead & (byte) 0xF8) == (byte) 0xF0 ? 2 : 1;
    }

    /**
     * The 0-based line and 0-based UTF-16 column of a source position.
     */
    public record LineCol(int line, int colUtf16) {
    }
}
