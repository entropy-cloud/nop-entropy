package io.nop.lint.core.node;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The byte-offset → (0-based line, 0-based UTF-16 column) conversion the L2
 * wire contract needs (roadmap item 20): ASCII passthrough, line counting,
 * multi-byte BMP characters counting one UTF-16 unit per code point, and
 * supplementary characters (4-byte UTF-8) counting two units (the surrogate
 * pair).
 */
public class TestSourcePositions {

    @Test
    public void asciiOffsetsPassThrough() {
        assertPosition("abc", 0, 0, 0);
        assertPosition("abc", 2, 0, 2);
    }

    @Test
    public void newlinesAdvanceLinesAndResetColumns() {
        // offset 3 is 'c' itself: first character of line 1
        assertPosition("ab\ncd", 3, 1, 0);
        // offset 5 is the end offset: past 'd', i.e. both characters of line 1
        assertPosition("ab\ncd", 5, 1, 2);
        assertPosition("ab\n\nx", 4, 2, 0);
    }

    @Test
    public void multiByteBmpCharactersCountOneUnit() {
        // 'é' is two UTF-8 bytes but one UTF-16 unit
        byte[] source = "aé\nb".getBytes(StandardCharsets.UTF_8);
        SourcePositions.LineCol b = SourcePositions.lineColUtf16(source, 4);
        assertEquals(1, b.line(), "'b' sits on the second line");
        assertEquals(0, b.colUtf16());

        SourcePositions.LineCol afterEacute = SourcePositions.lineColUtf16(source, 3);
        assertEquals(0, afterEacute.line());
        assertEquals(2, afterEacute.colUtf16(), "the two-byte 'é' counts one UTF-16 unit");
    }

    @Test
    public void supplementaryCharactersCountTwoUnits() {
        // '😀' is four UTF-8 bytes decoding to a surrogate pair (2 units)
        byte[] source = "a😀b".getBytes(StandardCharsets.UTF_8);
        SourcePositions.LineCol b = SourcePositions.lineColUtf16(source, 5);
        assertEquals(0, b.line());
        assertEquals(3, b.colUtf16(), "'a' (1) + '😀' (2) = column 3");
    }

    private void assertPosition(String text, int byteOffset, int line, int colUtf16) {
        SourcePositions.LineCol pos = SourcePositions.lineColUtf16(text.getBytes(StandardCharsets.UTF_8),
                byteOffset);
        assertEquals(line, pos.line());
        assertEquals(colUtf16, pos.colUtf16());
    }
}
