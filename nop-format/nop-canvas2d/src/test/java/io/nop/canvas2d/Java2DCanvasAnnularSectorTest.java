package io.nop.canvas2d;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

/**
 * WI9 对版回灌：圆环扇区 = 外弧（顺时针）+ 内弧（anticlockwise 回扫）闭合路径。
 * 扇区取 canvas 角度 [-90°, 0°)（右上象限，y 向下）。
 */
class Java2DCanvasAnnularSectorTest {

    private Java2DCanvas annularSector() {
        Java2DCanvas c = new Java2DCanvas(200, 200);
        c.beginPath();
        c.arc(100, 100, 80, -Math.PI / 2, 0, false);
        c.arc(100, 100, 40, 0, -Math.PI / 2, true);
        c.closePath();
        c.setFillStyleCss("#ff0000");
        c.fill();
        return c;
    }

    @Test
    void zrenderRealAnglesSectorFillsFullBand() {
        // zrender Sector 实测发出的第 1 扇区弧序列（外 4.71->6.72 cw；内 0.43->-1.57 ccw，
        // 扇区跨 0°）。回归钉：connect 缺陷（第二弧另起子路径）时带中点 (142,58) 空洞。
        Java2DCanvas c = new Java2DCanvas(200, 200);
        c.beginPath();
        c.arc(100, 100, 75, 4.71, 6.72, false);
        c.arc(100, 100, 56.25, 0.43, -1.57, true);
        c.closePath();
        c.setFillStyleCss("#ff0000");
        c.fill();
        BufferedImage img = c.getImage();
        // 带中点（r=60, 角 0°→(160,100)）与 −45°(142,58)、+60°方向(130,152 为 span 外对照)
        PixelAssert.assertOpaque(img, 160, 100);
        PixelAssert.assertOpaque(img, 142, 58);
        // 孔（r=30, 角 −45°）与 span 外（+90°）保持透明
        PixelAssert.assertTransparent(img, 121, 79);
        PixelAssert.assertTransparent(img, 100, 160);
    }

    @Test
    void annularSectorFillsBandAndKeepsHole() {
        Java2DCanvas c = annularSector();
        BufferedImage img = c.getImage();

        // 采样点全部在扇区角度 span 内（右上象限）：r·cos(-45°)=+0.707r, r·sin(-45°)=-0.707r
        // 带中点（r=60, -45°）→ (142, 58)：必须填充
        PixelAssert.assertOpaque(img, 142, 58);
        // 内缘附近（r=45）→ (132, 68)：必须填充
        PixelAssert.assertOpaque(img, 132, 68);
        // 外缘附近（r=75）→ (153, 47)：必须填充
        PixelAssert.assertOpaque(img, 153, 47);

        // 中孔（r=20, -45°）→ (114, 86)：必须透明
        PixelAssert.assertTransparent(img, 114, 86);
        // 带外（r=90, -45°）→ (164, 36)：必须透明
        PixelAssert.assertTransparent(img, 164, 36);
        // 角度 span 外（+90° 方向，r=60）→ (100, 160)：必须透明
        PixelAssert.assertTransparent(img, 100, 160);
    }
}
