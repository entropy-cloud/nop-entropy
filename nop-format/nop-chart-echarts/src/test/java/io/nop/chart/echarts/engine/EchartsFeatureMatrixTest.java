package io.nop.chart.echarts.engine;

import io.nop.chart.echarts.render.EChartsRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 Phase 2：特性面出图（legend/markLine/markPoint/rich/渐变 option/decal/graphic image）、
 * dataZoom 剥离回归（B1 裁定）、heatmap 数据面、aria DOM 容忍、progressive 泵（D27）。
 */
class EchartsFeatureMatrixTest {
    private final EchartsEngine engine = EchartsEngine.get();

    EchartsFeatureMatrixTest() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12).canDisplay('\u4e2d'),
                "environment lacks CJK glyphs — feature matrix is producer-machine scoped (D26)");
    }

    @Test
    void legendAndAxisAndMarkLineRender() {
        BufferedImage img = engine.renderCanvasSeam("""
                {"legend":{"data":["系列一","系列二"]},
                 "xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20],"name":"系列一",
                  "markLine":{"data":[{"type":"average"}]}},
                 {"type":"bar","data":[20,10,30],"name":"系列二"}]}
                """, 400, 300, null).image();
        ImageDiffAssert.assertNotBlank(img, "legend-markline");
        ImageDiffAssert.assertMatchesGolden(img, "feature-legend-markline");
    }

    @Test
    void markPointRenders() {
        BufferedImage img = engine.renderCanvasSeam("""
                {"xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20],
                  "markPoint":{"data":[{"type":"max"},{"type":"min"}]}}]}
                """, 400, 300, null).image();
        ImageDiffAssert.assertNotBlank(img, "markpoint");
    }

    @Test
    void richTextStyleRenders() {
        BufferedImage img = engine.renderCanvasSeam("""
                {"title":{"text":"富文本标题","textStyle":{"rich":{"bold":
                 {"fontSize":20,"fontWeight":"bold","color":"#c0504d"}}}},
                 "xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20],
                  "label":{"show":true,"formatter":"{bold|值}","rich":{"bold":{"color":"#333"}}}}]}
                """, 400, 300, null).image();
        ImageDiffAssert.assertNotBlank(img, "rich-text");
    }

    @Test
    void optionGradientRenders() {
        // option 形态的线性渐变（zrender JS 侧构建 ctx 渐变——桥接验证）
        BufferedImage img = engine.renderCanvasSeam("""
                {"xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20],"itemStyle":{"color":
                  {"type":"linear","x":0,"y":0,"x2":0,"y2":1,
                   "colorStops":[{"offset":0,"color":"#ff0000"},{"offset":1,"color":"#0000ff"}]}}}]}
                """, 300, 300, null).image();
        ImageDiffAssert.assertNotBlank(img, "option-gradient");
    }

    @Test
    void decalPatternRenders() {
        // decal 背景贴花 → createPattern 桥（dist:27942 decal canvas）
        BufferedImage img = engine.renderCanvasSeam("""
                {"series":[{"type":"pie","radius":"60%",
                 "data":[{"value":40,"name":"甲"},{"value":60,"name":"乙"}]}],
                 "aria":{"decal":{"decals":[{"symbol":"rect","rotation":0.5}]}}}
                """, 400, 300, null).image();
        ImageDiffAssert.assertNotBlank(img, "decal");
    }

    @Test
    void graphicImageDefersAndCounts() {
        // image:// 元素：loadImage defer + 计数（D18，静态渲染不拉网络图）
        EchartsEngine.BufferedImageHolder result = engine.renderCanvasSeam("""
                {"graphic":{"elements":[{"type":"image","style":{"image":"http://example.test/x.png",
                 "x":10,"y":10,"width":50,"height":50}}]}}
                """, 200, 200, null);
        assertTrue(result.imageRequests() >= 1, "image request must be counted");
    }

    @Test
    void dataZoomStrippingIsPixelNeutral() {
        // B1 裁定回归：dataZoom 剥离是终态——含/不含 dataZoom 的输出逐像素一致
        String base = """
                {"xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20]}]}""";
        String withZoom = """
                {"dataZoom":[{"type":"inside"},{"type":"slider","start":0,"end":100}],
                 "xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20]}]}""";
        // 两侧同走 normalize 管线（Jackson 数字序列化统一），隔离「剥离」这一变量
        EChartsRenderer renderer = new EChartsRenderer();
        BufferedImage without = engine.renderCanvasSeam(renderer.normalize(base), 400, 300, null).image();
        BufferedImage with = engine.renderCanvasSeam(renderer.normalize(withZoom), 400, 300, null).image();
        ImageDiffAssert.assertSimilar(without, with, 8, 0.0, "dataZoom-strip-neutral");
    }

    @Test
    void heatmapPixelDataEndToEnd() {
        // M3（修订后判据）：热力区（中央）非背景、角部背景——getImageData/putImageData 链路真实消费
        BufferedImage img = engine.renderCanvasSeam("""
                {"xAxis":{"type":"category","data":["一","二","三","四"]},
                 "yAxis":{"type":"category","data":["甲","乙"]},
                 "visualMap":{"min":0,"max":10,"calculable":true,"show":false},
                 "series":[{"type":"heatmap","data":[[0,0,5],[1,0,8],[2,1,3],[3,1,10]]}]}
                """, 400, 300, null, true).image();
        ImageDiffAssert.assertNotBlank(img, "heatmap");
        // JS 侧读回：通过桥 getImageData 断言中央像素非透明（热力渲染真实发生）
        String pixel = engine.evaluateForTest("""
                var root = globalThis.__activeRoot.__canvasBackend;
                var d = root.getImageData(150, 120, 80, 80);
                var arr = d.getData();
                var nonTransparent = 0;
                for (var i = 0; i < arr.length; i += 4)
                    if (arr[i + 3] > 10) nonTransparent++;
                String(nonTransparent > 0);
                """);
        assertEquals("true", pixel, "heatmap center must paint colored pixels");
        engine.disposeForTest();
    }

    @Test
    void ariaShowWorksWithDomToleranceFace() {
        // m6：canvas 模式 aria 走无保护 setAttribute——shim DOM 容忍面为必然项
        BufferedImage img = engine.renderCanvasSeam("""
                {"aria":{"show":true,"label":{"description":"无障碍描述"}},
                 "xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":[10,30,20]}]}
                """, 400, 300, null).image();
        ImageDiffAssert.assertNotBlank(img, "aria");
    }

    @Test
    void progressivePumpDrivesLargeData() {
        // D27：>3000 点（progressiveThreshold）→ pumpedTimers > 0 且未触顶（16 上限）
        StringBuilder data = new StringBuilder("[");
        for (int i = 0; i < 4000; i++) {
            if (i > 0)
                data.append(',');
            data.append(i % 97 + 5);
        }
        data.append(']');
        String option = """
                {"xAxis":{"type":"category"},"yAxis":{"type":"value"},
                 "series":[{"type":"bar","data":%s}]}""".formatted(data);
        EchartsEngine.BufferedImageHolder result = engine.renderCanvasSeam(option, 600, 400, null);
        ImageDiffAssert.assertNotBlank(result.image(), "progressive");
        assertTrue(result.pumpedTimers() > 0,
                "progressive frames must be pumped, got " + result.pumpedTimers());
        assertTrue(result.pumpedTimers() < 16,
                "pump must reach quiescence without hitting cap, got " + result.pumpedTimers());
    }
}
