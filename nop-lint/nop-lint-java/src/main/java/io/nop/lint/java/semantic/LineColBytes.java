package io.nop.lint.java.semantic;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Converts 1-based line / 1-based UTF-16 column positions to UTF-8 byte
 * offsets (the JavaParser ⇄ tree-sitter bridge direction, roadmap item 26).
 *
 * <p>JavaParser positions count lines and UTF-16 code units from 1, and its
 * {@code Range} end column is <em>inclusive</em> — the exclusive end byte of
 * a range is therefore {@code byteOf(endLine, endCol + 1)} (the plan's F3
 * trap: a naive arithmetic conversion drifts on multi-byte lines — measured
 * off-by-4 on a CJK+emoji line). Conversion walks the line's bytes counting
 * UTF-16 units, which is exact for every input.</p>
 *
 * <p>Positions beyond the source degrade to the nearest valid bound (a line
 * past the end yields the source length), so range-end conversion at the
 * file tail works without special cases.</p>
 */
public final class LineColBytes {

    private final byte[] source;
    private final int[] lineStartBytes;

    public LineColBytes(byte[] source) {
        this.source = source;
        int lines = 1;
        for (byte b : source) {
            if (b == '\n') {
                lines++;
            }
        }
        lineStartBytes = new int[lines + 1];
        Arrays.fill(lineStartBytes, source.length);
        int index = 1;
        lineStartBytes[0] = 0;
        for (int i = 0; i < source.length; i++) {
            if (source[i] == '\n') {
                lineStartBytes[index++] = i + 1;
            }
        }
    }

    /**
     * The UTF-8 byte offset of the 1-based line / 1-based UTF-16 column. A
     * column past the line's end yields the next line's start byte (the
     * position sits between the lines).
     */
    public int byteOf(int line, int colUtf16) {
        if (line < 1) {
            throw new IllegalArgumentException("line is 1-based: " + line);
        }
        if (line > lineCount()) {
            return source.length;
        }
        int start = lineStartBytes[line - 1];
        int end = lineStartBytes[line];
        int unitsLeft = colUtf16 - 1;
        int i = start;
        while (i < end && unitsLeft > 0) {
            int units = utf16Units(i);
            if (units > unitsLeft) {
                break; // the position points into a surrogate pair
            }
            unitsLeft -= units;
            i += sequenceLength(i);
        }
        return i;
    }

    /**
     * The 1-based line of a byte offset.
     */
    public int lineOfByte(int byteOffset) {
        int line = 1;
        for (int i = 0; i < lineStartBytes.length - 1; i++) {
            if (byteOffset >= lineStartBytes[i + 1]) {
                line++;
            } else {
                break;
            }
        }
        return line;
    }

    /**
     * The 1-based line count (a trailing newline does not start an extra
     * line).
     */
    public int lineCount() {
        return lineStartBytes.length - 1;
    }

    private int sequenceLength(int i) {
        byte lead = source[i];
        if (lead >= 0) {
            return 1;
        }
        return utf8SequenceLength(lead);
    }

    private int utf16Units(int i) {
        byte lead = source[i];
        if (lead >= 0) {
            return 1;
        }
        int len = utf8SequenceLength(lead);
        // a 4-byte UTF-8 sequence encodes one supplementary code point =
        // two UTF-16 code units (the source is valid UTF-8 by construction)
        return len >= 4 ? 2 : 1;
    }

    private int utf8SequenceLength(byte lead) {
        if ((lead & 0xE0) == 0xC0) {
            return 2;
        }
        if ((lead & 0xF0) == 0xE0) {
            return 3;
        }
        if ((lead & 0xF8) == 0xF0) {
            return 4;
        }
        return 1; // continuation bytes cannot start a sequence in valid UTF-8
    }

    /**
     * Test-support: the canonical UTF-8 form of the conversion input.
     */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
