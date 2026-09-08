package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TreeSitterException;

/**
 * A source edit in the upstream {@code TSInputEdit} sense: the byte span
 * {@code [startByte, oldEndByte)} of the old source was replaced by the byte
 * span {@code [startByte, newEndByte)} of the new source. Insertions have
 * {@code oldEndByte == startByte}; deletions have {@code newEndByte == startByte}.
 *
 * @param startByte   first byte of the edited span (same offset in both sources)
 * @param oldEndByte  one past the last replaced byte in the old source
 * @param newEndByte  one past the last replacing byte in the new source
 * @param startPoint  position of {@code startByte}
 * @param oldEndPoint position of {@code oldEndByte} in the old source
 * @param newEndPoint position of {@code newEndByte} in the new source
 */
public record TSInputEdit(int startByte, int oldEndByte, int newEndByte,
                          TSPoint startPoint, TSPoint oldEndPoint, TSPoint newEndPoint) {

    public TSInputEdit {
        if (startPoint == null || oldEndPoint == null || newEndPoint == null) {
            throw new TreeSitterException("ts input edit points must not be null");
        }
        if (startByte < 0) {
            throw new TreeSitterException("ts input edit startByte must be >= 0, got " + startByte);
        }
        if (oldEndByte < startByte) {
            throw new TreeSitterException("ts input edit oldEndByte " + oldEndByte
                    + " must be >= startByte " + startByte);
        }
        if (newEndByte < startByte) {
            throw new TreeSitterException("ts input edit newEndByte " + newEndByte
                    + " must be >= startByte " + startByte);
        }
    }

    /**
     * Builds an edit over byte offsets with all three points computed from the
     * corresponding sources: {@code startPoint}/{@code oldEndPoint} from the old
     * source, {@code newEndPoint} from the new source.
     */
    public static TSInputEdit of(byte[] oldSource, byte[] newSource,
                                 int startByte, int oldEndByte, int newEndByte) {
        if (oldSource == null || newSource == null) {
            throw new TreeSitterException("edit sources must not be null");
        }
        if (oldEndByte > oldSource.length) {
            throw new TreeSitterException("edit oldEndByte " + oldEndByte
                    + " out of range [0," + oldSource.length + "]");
        }
        if (newEndByte > newSource.length) {
            throw new TreeSitterException("edit newEndByte " + newEndByte
                    + " out of range [0," + newSource.length + "]");
        }
        return new TSInputEdit(startByte, oldEndByte, newEndByte,
                TSPoint.fromByteOffset(oldSource, startByte),
                TSPoint.fromByteOffset(oldSource, oldEndByte),
                TSPoint.fromByteOffset(newSource, newEndByte));
    }

    /**
     * True when the edit replaces no bytes and inserts no bytes.
     */
    public boolean isNoop() {
        return oldEndByte == startByte && newEndByte == startByte;
    }
}
