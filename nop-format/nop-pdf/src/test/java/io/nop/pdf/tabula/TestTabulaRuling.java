package io.nop.pdf.tabula;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * tabula 线段几何语义：近水平/近垂直线归一化、方向判定、位置/端点读写、
 * 垂直/平行邻接判定与裁剪求交
 */
public class TestTabulaRuling {

    @Test
    public void testOrientationClassification() {
        Ruling vertical = new Ruling(0f, 5f, 0f, 100f);
        assertTrue(vertical.vertical());
        assertFalse(vertical.horizontal());
        assertFalse(vertical.oblique());

        Ruling horizontal = new Ruling(7f, 0f, 100f, 0f);
        assertTrue(horizontal.horizontal());
        assertFalse(horizontal.vertical());

        Ruling oblique = new Ruling(0f, 0f, 30f, 40f);
        assertTrue(oblique.oblique());
        assertEquals(50.0, oblique.length(), 1e-6);
    }

    @Test
    public void testNormalizeSnapsSlightlySlantedLine() {
        // 斜率约 1 度的线必须被归一化为严格水平（y 相等）
        Ruling slanted = new Ruling(10f, 0f, 500f, 5f);
        slanted.normalize();
        assertEquals(slanted.getY1(), slanted.getY2(), 1e-6);
        assertTrue(slanted.horizontal());
    }

    @Test
    public void testGetPositionAndSetPositionForVerticalRuling() {
        Ruling vertical = new Ruling(10f, 5f, 0f, 100f);
        assertEquals(5f, vertical.getPosition(), 1e-6);
        assertEquals(10f, vertical.getStart(), 1e-6);
        assertEquals(110f, vertical.getEnd(), 1e-6);

        vertical.setPosition(9f);
        assertEquals(9f, vertical.getLeft(), 1e-6);
        assertEquals(9f, vertical.getRight(), 1e-6);

        vertical.setStart(0f);
        vertical.setEnd(50f);
        assertEquals(0f, vertical.getTop(), 1e-6);
        assertEquals(50f, vertical.getBottom(), 1e-6);
    }

    @Test
    public void testObliquePositionAccessThrows() {
        Ruling oblique = new Ruling(0f, 0f, 30f, 40f);
        assertThrows(UnsupportedOperationException.class, oblique::getPosition);
        assertThrows(UnsupportedOperationException.class, () -> oblique.setPosition(1f));
    }

    @Test
    public void testPerpendicularAndColinear() {
        Ruling v = new Ruling(0f, 5f, 0f, 100f);
        Ruling h = new Ruling(50f, 0f, 100f, 0f);
        assertTrue(v.perpendicularTo(h));
        assertTrue(h.perpendicularTo(v));
        assertFalse(v.perpendicularTo(new Ruling(0f, 6f, 0f, 100f)));

        // 点落在垂直线的包围盒内（x==lineX，y 在线段范围内）即共线
        assertTrue(v.colinear(new Point2D.Float(5f, 50f)));
        assertFalse(v.colinear(new Point2D.Float(6f, 50f)));
        assertFalse(v.colinear(new Point2D.Float(5f, 999f)));
    }

    @Test
    public void testIntersectWithClipRectangle() {
        Ruling vertical = new Ruling(0f, 5f, 0f, 100f);
        Rectangle2D clip = new Rectangle2D.Float(0f, 20f, 100f, 10f);
        Ruling clipped = vertical.intersect(clip);
        assertEquals(20f, clipped.getTop(), 1e-6);
        assertEquals(30f, clipped.getBottom(), 1e-6);
        // 完全在裁剪区外的线段原样返回（不裁剪）
        Rectangle2D far = new Rectangle2D.Float(0f, 500f, 10f, 10f);
        assertSame(vertical, vertical.intersect(far));
    }

    @Test
    public void testExpandGrowsBothEnds() {
        Ruling vertical = new Ruling(10f, 5f, 0f, 100f);
        Ruling expanded = vertical.expand(5f);
        assertEquals(5f, expanded.getTop(), 1e-6);
        assertEquals(115f, expanded.getBottom(), 1e-6);
    }

    @Test
    public void testNearlyIntersectsAcrossGap() {
        // 同一水平线上的两条线段留 2px 空隙：各扩展1px后端点相接
        Ruling a = new Ruling(0f, 0f, 100f, 0f);
        Ruling b = new Ruling(0f, 102f, 50f, 0f);
        assertTrue(a.nearlyIntersects(b));
        // 相距很远的平行线不相交
        Ruling far = new Ruling(50f, 0f, 100f, 0f);
        assertFalse(a.nearlyIntersects(far));
    }
}
