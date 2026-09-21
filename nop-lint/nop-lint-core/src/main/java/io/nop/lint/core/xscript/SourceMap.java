package io.nop.lint.core.xscript;

import java.util.Arrays;

/**
 * Byte-offset to 1-based line/column map over one parsed source (design 07
 * §2.2: {@code NodeWrapper.range()} reports source positions, which the
 * {@link io.nop.lint.core.node.SourceRange} byte offsets alone cannot
 * provide). Built once per file per run from the parsed source bytes.
 *
 * <p>Line starts are UTF-8 byte offsets of the first byte of each line; the
 * byte {@code 0x0A} never occurs inside a multi-byte sequence, so plain byte
 * scanning is correct for every input. Columns count bytes from the line
 * start and are 1-based, matching the 1-based line convention of the
 * diagnostic fixture format.</p>
 */
public final class SourceMap {

    private final byte[] source;
    private final int[] lineStarts;

    /**
     * Builds the position map over the given UTF-8 source bytes.
     */
    public SourceMap(byte[] source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.source = source;
        int lineCount = 1;
        for (byte b : source) {
            if (b == '\n') {
                lineCount++;
            }
        }
        int[] lineStarts = new int[lineCount];
        int index = 1;
        for (int i = 0; i < source.length; i++) {
            if (source[i] == '\n' && index < lineCount) {
                lineStarts[index++] = i + 1;
            }
        }
        this.lineStarts = lineStarts;
    }

    /**
     * The parsed source bytes this map was built from.
     */
    public byte[] source() {
        return source;
    }

    /**
     * The 1-based line that contains {@code byteOffset}. The offset one past
     * the last source byte belongs to the last line (zero-length trailing
     * nodes report a position).
     */
    public int lineOf(int byteOffset) {
        if (byteOffset < 0 || byteOffset > source.length) {
            throw new IllegalArgumentException("byteOffset out of range: " + byteOffset);
        }
        int line = Arrays.binarySearch(lineStarts, byteOffset);
        if (line >= 0) {
            return line + 1;
        }
        return -line - 1;
    }

    /**
     * The 1-based byte column of {@code byteOffset} within its line.
     */
    public int columnOf(int byteOffset) {
        int line = lineOf(byteOffset);
        return byteOffset - lineStarts[line - 1] + 1;
    }
}
