package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TreeSitterException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TSEditValueTypesTest {

    @Test
    void pointRejectsNegativeRowAndColumn() {
        assertThrows(TreeSitterException.class, () -> new TSPoint(-1, 0));
        assertThrows(TreeSitterException.class, () -> new TSPoint(0, -1));
    }

    @Test
    void pointFromByteOffsetTracksRowsAndColumns() {
        byte[] source = "ab\ncd\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(new TSPoint(0, 0), TSPoint.fromByteOffset(source, 0));
        assertEquals(new TSPoint(0, 2), TSPoint.fromByteOffset(source, 2));
        assertEquals(new TSPoint(1, 0), TSPoint.fromByteOffset(source, 3));
        assertEquals(new TSPoint(1, 2), TSPoint.fromByteOffset(source, 5));
        assertEquals(new TSPoint(2, 0), TSPoint.fromByteOffset(source, 6));
    }

    @Test
    void pointColumnsCountUtf8BytesNotCodepoints() {
        byte[] source = "é汉\nx".getBytes(StandardCharsets.UTF_8);
        assertEquals(2, "é".getBytes(StandardCharsets.UTF_8).length);
        assertEquals(3, "汉".getBytes(StandardCharsets.UTF_8).length);
        assertEquals(new TSPoint(0, 5), TSPoint.fromByteOffset(source, 5));
        assertEquals(new TSPoint(1, 0), TSPoint.fromByteOffset(source, 6));
        assertEquals(new TSPoint(1, 1), TSPoint.fromByteOffset(source, 7));
    }

    @Test
    void pointFromByteOffsetRejectsOutOfRange() {
        byte[] source = "ab".getBytes(StandardCharsets.UTF_8);
        assertThrows(TreeSitterException.class, () -> TSPoint.fromByteOffset(source, -1));
        assertThrows(TreeSitterException.class, () -> TSPoint.fromByteOffset(source, 3));
        assertThrows(TreeSitterException.class, () -> TSPoint.fromByteOffset(null, 0));
    }

    @Test
    void pointAtEmptySourceAndOffsetZero() {
        assertEquals(TSPoint.ZERO, TSPoint.fromByteOffset(new byte[0], 0));
    }

    @Test
    void rangeValidatesBounds() {
        assertThrows(TreeSitterException.class,
                () -> new TSRange(TSPoint.ZERO, TSPoint.ZERO, -1, 0));
        assertThrows(TreeSitterException.class,
                () -> new TSRange(TSPoint.ZERO, TSPoint.ZERO, 2, 1));
        assertThrows(TreeSitterException.class,
                () -> new TSRange(null, TSPoint.ZERO, 0, 1));
    }

    @Test
    void rangeOfComputesPointsFromSource() {
        byte[] source = "ab\ncdef".getBytes(StandardCharsets.UTF_8);
        TSRange range = TSRange.of(source, 3, 6);
        assertEquals(new TSPoint(1, 0), range.startPoint());
        assertEquals(new TSPoint(1, 3), range.endPoint());
        assertEquals(3, range.startByte());
        assertEquals(6, range.endByte());
        assertTrue(TSRange.overlapsOrTouches(range, TSRange.of(source, 6, 7)));
        assertTrue(TSRange.overlapsOrTouches(TSRange.of(source, 0, 4), range));
        assertThrows(TreeSitterException.class, () -> TSRange.of(source, 5, 99));
    }

    @Test
    void inputEditValidatesByteOrder() {
        assertThrows(TreeSitterException.class,
                () -> new TSInputEdit(-1, 0, 0, TSPoint.ZERO, TSPoint.ZERO, TSPoint.ZERO));
        assertThrows(TreeSitterException.class,
                () -> new TSInputEdit(2, 1, 1, TSPoint.ZERO, TSPoint.ZERO, TSPoint.ZERO));
        assertThrows(TreeSitterException.class,
                () -> new TSInputEdit(0, 1, 0, TSPoint.ZERO, null, TSPoint.ZERO));
    }

    @Test
    void inputEditOfComputesPointsFromBothSources() {
        byte[] oldSource = "ab\ncdef".getBytes(StandardCharsets.UTF_8);
        byte[] newSource = "ab\nXYcdef".getBytes(StandardCharsets.UTF_8);
        TSInputEdit edit = TSInputEdit.of(oldSource, newSource, 3, 3, 5);
        assertEquals(3, edit.startByte());
        assertEquals(3, edit.oldEndByte());
        assertEquals(5, edit.newEndByte());
        assertEquals(new TSPoint(1, 0), edit.startPoint());
        assertEquals(new TSPoint(1, 0), edit.oldEndPoint());
        assertEquals(new TSPoint(1, 2), edit.newEndPoint());
        assertTrue(edit.isNoop() == false);
        assertTrue(TSInputEdit.of(oldSource, oldSource, 2, 2, 2).isNoop());
    }

    @Test
    void inputEditOfRejectsOutOfBounds() {
        byte[] oldSource = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] newSource = "abc".getBytes(StandardCharsets.UTF_8);
        assertThrows(TreeSitterException.class, () -> TSInputEdit.of(oldSource, newSource, 0, 4, 4));
        assertThrows(TreeSitterException.class, () -> TSInputEdit.of(oldSource, newSource, 0, 2, 4));
        assertThrows(TreeSitterException.class, () -> TSInputEdit.of(null, newSource, 0, 1, 1));
    }
}
