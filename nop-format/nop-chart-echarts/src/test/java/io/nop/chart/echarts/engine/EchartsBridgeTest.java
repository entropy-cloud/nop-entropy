package io.nop.chart.echarts.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 Phase 2 图像桥测试（D17）：fake canvas drawImage 与 getImageData/putImageData
 * 经 shim 直测（heatmap/brush 合成路径的基础设施）。
 */
class EchartsBridgeTest {
    private final EchartsEngine engine = EchartsEngine.get();

    @Test
    void fakeCanvasDrawImageComposites() {
        // brush 合成形态（dist:69620s）：canvas 源绘制进另一 canvas
        String script = """
                var src = __bridgeCreateCanvas(20, 20);
                var sctx = src.getContext('2d');
                sctx.fillStyle = '#ff0000';
                sctx.fillRect(0, 0, 20, 20);
                var dst = __bridgeCreateCanvas(20, 20);
                var dctx = dst.getContext('2d');
                dctx.drawImage(src, 0, 0);
                var px = dctx.getImageData(10, 10, 1, 1).data;
                px[0] + ',' + px[1] + ',' + px[2];
                """;
        assertEquals("255,0,0", engine.evaluateForTest(script));
    }

    @Test
    void getImageDataPutImageDataRoundTrip() {
        // heatmap 形态（dist:69625-69653）：全画布读取 → 写回
        String script = """
                var c = __bridgeCreateCanvas(30, 30);
                var ctx = c.getContext('2d');
                ctx.fillStyle = '#5470c6';
                ctx.fillRect(5, 5, 20, 20);
                var imageData = ctx.getImageData(0, 0, 30, 30);
                var d = imageData.data;
                var inside = [d[10 * 30 * 4 + 15 * 4], d[10 * 30 * 4 + 15 * 4 + 1], d[10 * 30 * 4 + 15 * 4 + 2]];
                var outside = [d[0], d[1], d[2], d[3]];
                var result = (inside[0] === 84 && inside[1] === 112 && inside[2] === 198 ? 'in-ok' : 'in-bad') + '|' +
                             (outside[3] === 0 ? 'out-ok' : 'out-bad');
                d[0] = 0; d[1] = 255; d[2] = 0; d[3] = 255;
                ctx.putImageData(imageData, 0, 0);
                var after = c.getContext('2d').getImageData(0, 0, 1, 1).data;
                result += '|' + (after[1] === 255 && after[0] === 0 ? 'put-ok' : 'put-bad');
                result;
                """;
        assertEquals("in-ok|out-ok|put-ok", engine.evaluateForTest(script));
    }

    @Test
    void canvasResizeRebuildsBuffer() {
        // D22（复核修订）：异值 resize 重建缓冲（heatmap brush canvas 合法路径）
        String script = """
                var c = __bridgeCreateCanvas(20, 20);
                var ctx = c.getContext('2d');
                ctx.fillStyle = '#ff0000';
                ctx.fillRect(0, 0, 20, 20);
                c.width = 40;
                var after = c.getContext('2d').getImageData(10, 10, 1, 1).data;
                c.width + 'x' + c.height + '|' + after[3];
                """;
        // 重建后缓冲清空（浏览器 resize 语义）：alpha=0
        assertEquals("40x20|0", engine.evaluateForTest(script));
    }

    @Test
    void sameValueResizeIsNoOp() {
        String script = """
                var c = __bridgeCreateCanvas(20, 20);
                var ctx = c.getContext('2d');
                ctx.fillStyle = '#ff0000';
                ctx.fillRect(0, 0, 20, 20);
                c.width = 20;
                var after = c.getContext('2d').getImageData(10, 10, 1, 1).data;
                c.width + 'x' + c.height + '|' + after[0];
                """;
        // 同值 no-op：内容保留
        assertEquals("20x20|255", engine.evaluateForTest(script));
    }

    @Test
    void renderWithImageRequestLogsAndCounts() {
        // D18：loadImage defer 登记（静态渲染忽略 image 请求）
        int counts = engine.renderCanvasForTest("""
                {"graphic":{"elements":[{"type":"image","style":{"image":"http://x/y.png",
                 "x":10,"y":10,"width":20,"height":20}}]}}
                """, 100, 100);
        assertTrue(counts >= 1, "image request must be counted, got " + counts);
    }

    @Test
    void annularSectorViaShimFillsFullBand() throws Exception {
        // WI9 回归钉（plan 2367 连接缺陷修复）：zrender Sector 真实弧序列
        // （外 4.71->6.72 cw；内 0.43->-1.57 ccw）在 shim 画布上必须填满环带。
        // connect 缺陷（第二弧另起子路径）时带中点 (160,100) 空洞。
        EchartsEngine engine = EchartsEngine.get();
        String pre = """
                var cv = global.__bridgeCreateCanvas(200, 200);
                var ctx = cv.getContext('2d');
                ctx.beginPath();
                ctx.arc(100, 100, 75, 4.71, 6.72, false);
                ctx.arc(100, 100, 56.25, 0.43, -1.57, true);
                ctx.closePath();
                ctx.fillStyle = '#ff0000';
                ctx.fill();
                var data = ctx.getImageData(0, 0, 200, 200).data;
                var bin = '';
                for (var i = 0; i < data.length; i += 4) {
                  bin += String.fromCharCode(data[i], data[i + 1], data[i + 2], data[i + 3]);
                }
                global.__pixels = bin;
                """;
        engine.renderCanvasSeam("{}", 200, 200, pre);
        String pixels = engine.evaluateForTest("global.__pixels");
        assertNotNull(pixels);
        byte[] bytes = pixels.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(200, 200,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 200; y++)
            for (int x = 0; x < 200; x++) {
                int k = (y * 200 + x) * 4;
                img.setRGB(x, y, (bytes[k + 3] & 0xFF) << 24 | (bytes[k] & 0xFF) << 16
                        | (bytes[k + 1] & 0xFF) << 8 | (bytes[k + 2] & 0xFF));
            }
        // 带内三点（r=60：角 0°/−45°/+25°）全部填充；孔（r=30）透明
        assertAlpha(img, 160, 100);
        assertAlpha(img, 142, 58);
        assertAlpha(img, 161, 65); // r=70, 角 -30°
        assertTransparent(img, 121, 79);
    }

    private void assertAlpha(java.awt.image.BufferedImage img, int x, int y) {
        int a = (img.getRGB(x, y) >>> 24) & 0xFF;
        org.junit.jupiter.api.Assertions.assertTrue(a > 0,
                "expected filled pixel at (" + x + "," + y + "), alpha=" + a);
    }

    private void assertTransparent(java.awt.image.BufferedImage img, int x, int y) {
        int a = (img.getRGB(x, y) >>> 24) & 0xFF;
        org.junit.jupiter.api.Assertions.assertEquals(0, a,
                "expected transparent pixel at (" + x + "," + y + "), alpha=" + a);
    }
}
