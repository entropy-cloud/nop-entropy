package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TreeSitterException;

/**
 * Half-open byte range with its boundary positions, mirroring the upstream
 * {@code TSRange}: {@code [startByte, endByte)} in one source's coordinates.
 *
 * @param startPoint position of the first byte
 * @param endPoint   position one past the last byte
 * @param startByte  byte offset of the first byte
 * @param endByte    byte offset one past the last byte
 */
public record TSRange(TSPoint startPoint, TSPoint endPoint, int startByte, int endByte) {

    public TSRange {
        if (startPoint == null || endPoint == null) {
            throw new TreeSitterException("ts range points must not be null");
        }
        if (startByte < 0) {
            throw new TreeSitterException("ts range startByte must be >= 0, got " + startByte);
        }
        if (endByte < startByte) {
            throw new TreeSitterException("ts range endByte " + endByte + " must be >= startByte " + startByte);
        }
    }

    /**
     * Builds the range {@code [startByte, endByte)} of {@code source} with the
     * boundary points computed from the same source.
     */
    public static TSRange of(byte[] source, int startByte, int endByte) {
        if (source == null) {
            throw new TreeSitterException("source must not be null");
        }
        if (endByte < startByte) {
            throw new TreeSitterException("range endByte " + endByte + " must be >= startByte " + startByte);
        }
        if (endByte > source.length) {
            throw new TreeSitterException("range endByte " + endByte + " out of range [0," + source.length + "]");
        }
        return new TSRange(TSPoint.fromByteOffset(source, startByte), TSPoint.fromByteOffset(source, endByte),
                startByte, endByte);
    }

    /**
     * True when the two ranges overlap or touch: {@code a.endByte >= b.startByte}
     * and {@code b.endByte >= a.startByte} — the merge predicate of the upstream
     * changed-range coalescing.
     */
    public static boolean overlapsOrTouches(TSRange a, TSRange b) {
        return a.endByte >= b.startByte && b.endByte >= a.startByte;
    }
}
