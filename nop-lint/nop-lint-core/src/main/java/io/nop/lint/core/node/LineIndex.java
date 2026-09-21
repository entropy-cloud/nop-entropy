package io.nop.lint.core.node;

import java.nio.charset.StandardCharsets;

/**
 * Maps UTF-8 byte offsets of one source text to 1-based line numbers (the
 * design 03 §4.2 v1 decision implemented once, here: the start byte and the
 * last byte of a {@code Diagnostic.range} each fall on their own line), and
 * the reverse direction used by the suppression layer (design 09 §2): the
 * byte span of a 1-based line.
 *
 * <p>Newline counting runs over the UTF-8 bytes; the byte {@code 0x0A} never
 * occurs inside a multi-byte sequence, so plain byte scanning is correct for
 * every input, including non-ASCII sources.</p>
 */
public final class LineIndex {

    private final byte[] utf8;
    private final int[] lineStartBytes;

    public LineIndex(String source) {
        this(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds the index over raw UTF-8 source bytes (the form
     * {@link LintTree#source()} hands out, so suppression consumers never
     * need to decode the source into a String).
     */
    public LineIndex(byte[] utf8) {
        this.utf8 = utf8;
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
     * The number of lines (a trailing newline does not start an extra line).
     */
    public int lineCount() {
        return lineStartBytes.length;
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
     * The inclusive start byte of the 1-based {@code line}. A line number
     * beyond {@link #lineCount()} has no bytes: the start equals the source
     * length (an empty trailing span).
     */
    public int lineStartByte(int line) {
        if (line < 1) {
            throw new IllegalArgumentException("line is 1-based: " + line);
        }
        if (line > lineStartBytes.length) {
            return utf8.length;
        }
        return lineStartBytes[line - 1];
    }

    /**
     * The exclusive end byte of the 1-based {@code line}: the start of the
     * next line, or the source length for the last line. The span
     * {@code [lineStartByte(line), lineEndByte(line))} covers the line
     * including its newline.
     */
    public int lineEndByte(int line) {
        return lineStartByte(line + 1);
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
