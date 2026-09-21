package io.nop.lint.core.testing;

import io.nop.lint.core.node.SourceRange;

import java.nio.charset.StandardCharsets;

/**
 * Maps UTF-8 byte offsets of one source text to 1-based line numbers (the
 * design 03 §4.2 v1 decision implemented once, here: the start byte and the
 * last byte of a {@code Diagnostic.range} each fall on their own line).
 *
 * <p>Newline counting runs over the UTF-8 bytes; the byte {@code 0x0A} never
 * occurs inside a multi-byte sequence, so plain byte scanning is correct for
 * every input, including non-ASCII sources.</p>
 */
public final class LineIndex {

    private final int[] lineStartBytes;

    public LineIndex(String source) {
        byte[] utf8 = source.getBytes(StandardCharsets.UTF_8);
        int lineCount = 1;
        for (byte b : utf8) {
            if (b == '\n') {
                lineCount++;
            }
        }
        lineStartBytes = new int[lineCount];
        int index = 1;
        for (int i = 0; i < utf8.length; i++) {
            if (utf8[i] == '\n') {
                lineStartBytes[index++] = i + 1;
            }
        }
    }

    /**
     * The 1-based line that contains {@code byteOffset}.
     */
    public int lineOfByte(int byteOffset) {
        if (byteOffset < 0) {
            throw new IllegalArgumentException("byteOffset must not be negative: " + byteOffset);
        }
        int low = 0;
        int high = lineStartBytes.length - 1;
        int line = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lineStartBytes[mid] <= byteOffset) {
                line = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return line + 1;
    }

    /**
     * The 1-based line of a range's start byte (range start is inclusive).
     */
    public int startLine(SourceRange range) {
        return lineOfByte(range.startByte());
    }

    /**
     * The 1-based line of a range's last byte: the range end is exclusive,
     * so the end line is the line of byte {@code endByte - 1}. An empty
     * range reports its start line.
     */
    public int endLine(SourceRange range) {
        int last = range.endByte() - 1;
        if (last < range.startByte()) {
            last = range.startByte();
        }
        return lineOfByte(last);
    }
}
