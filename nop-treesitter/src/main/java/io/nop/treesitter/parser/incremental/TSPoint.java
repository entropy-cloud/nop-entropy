package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TreeSitterException;

/**
 * Source position in the upstream {@code TSPoint} sense: a zero-based {@code row}
 * and a zero-based {@code column} counted in <b>UTF-8 bytes</b> since the last
 * line break, not in codepoints — C {@code point.h} semantics.
 *
 * @param row    zero-based line index
 * @param column zero-based byte offset within the line
 */
public record TSPoint(int row, int column) {

    public TSPoint {
        if (row < 0) {
            throw new TreeSitterException("ts point row must be >= 0, got " + row);
        }
        if (column < 0) {
            throw new TreeSitterException("ts point column must be >= 0, got " + column);
        }
    }

    public static final TSPoint ZERO = new TSPoint(0, 0);

    /**
     * The position of {@code offset} within {@code source}: rows count
     * {@code '\n'} bytes, columns count UTF-8 bytes since the last line break.
     */
    public static TSPoint fromByteOffset(byte[] source, int offset) {
        if (source == null) {
            throw new TreeSitterException("source must not be null");
        }
        if (offset < 0 || offset > source.length) {
            throw new TreeSitterException("byte offset " + offset + " out of range [0," + source.length + "]");
        }
        int row = 0;
        int lineStart = 0;
        for (int i = 0; i < offset; i++) {
            if (source[i] == '\n') {
                row++;
                lineStart = i + 1;
            }
        }
        return new TSPoint(row, offset - lineStart);
    }
}
