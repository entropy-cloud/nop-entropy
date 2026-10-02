package io.nop.pdf.tabula;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * tabula 矩形几何语义：面积/重叠量/重叠比计算，merge 并集与 boundingBoxOf 包围盒
 */
public class TestTabulaRectangle {

    private static Rectangle rect(float top, float left, float width, float height) {
        return new Rectangle(top, left, width, height);
    }

    @Test
    public void testAreaAndEdges() {
        Rectangle r = rect(10, 20, 30, 40);
        assertEquals(1200f, r.getArea(), 1e-6);
        assertEquals(10f, r.getTop(), 1e-6);
        assertEquals(20f, r.getLeft(), 1e-6);
        assertEquals(50f, r.getRight(), 1e-6);
        assertEquals(50f, r.getBottom(), 1e-6);
    }

    @Test
    public void testVerticalAndHorizontalOverlap() {
        Rectangle a = rect(0, 0, 100, 100);
        Rectangle b = rect(50, 50, 100, 100);
        // 垂直方向交集 [50,100] 高度50
        assertEquals(50f, a.verticalOverlap(b), 1e-6);
        // 水平方向交集 [50,100] 宽度50
        assertEquals(50f, a.horizontalOverlap(b), 1e-6);
        assertEquals(true, a.verticallyOverlaps(b));
        assertEquals(true, a.horizontallyOverlaps(b));

        Rectangle apart = rect(200, 200, 10, 10);
        assertEquals(0f, a.verticalOverlap(apart), 1e-6);
        assertEquals(false, a.verticallyOverlaps(apart));
    }

    @Test
    public void testVerticalOverlapRatioContainsOther() {
        Rectangle outer = rect(0, 0, 100, 100);
        // other 完全落在 outer 内部：交集高度/较小高度 = 40/40 = 1
        Rectangle inner = rect(30, 0, 10, 40);
        assertEquals(1.0f, outer.verticalOverlapRatio(inner), 1e-6);
    }

    @Test
    public void testVerticalOverlapRatioPartial() {
        // a: [0,100], b: [50,150]：交集 [50,100] 高50，较小高度100 → 0.5
        Rectangle a = rect(0, 0, 100, 100);
        Rectangle b = rect(50, 0, 100, 100);
        assertEquals(0.5f, a.verticalOverlapRatio(b), 1e-6);
    }

    @Test
    public void testOverlapRatioOfIdenticalRectIsOne() {
        Rectangle a = rect(0, 0, 100, 100);
        Rectangle b = rect(0, 0, 100, 100);
        assertEquals(1.0f, a.overlapRatio(b), 1e-6);
        // 不相交的矩形重叠比为0
        Rectangle far = rect(500, 500, 10, 10);
        assertEquals(0.0f, a.overlapRatio(far), 1e-6);
    }

    @Test
    public void testMergeProducesUnion() {
        Rectangle a = rect(0, 0, 100, 100);
        Rectangle b = rect(50, 50, 100, 100);
        a.merge(b);
        assertEquals(0f, a.getTop(), 1e-6);
        assertEquals(0f, a.getLeft(), 1e-6);
        assertEquals(150f, a.getBottom(), 1e-6);
        assertEquals(150f, a.getRight(), 1e-6);
    }

    @Test
    public void testBoundingBoxOfList() {
        Rectangle box = Rectangle.boundingBoxOf(Arrays.asList(
                rect(0, 0, 10, 10), rect(20, 30, 5, 5)));
        assertEquals(0f, box.getTop(), 1e-6);
        assertEquals(0f, box.getLeft(), 1e-6);
        assertEquals(35f, box.getRight(), 1e-6);
        assertEquals(25f, box.getBottom(), 1e-6);
    }
}
