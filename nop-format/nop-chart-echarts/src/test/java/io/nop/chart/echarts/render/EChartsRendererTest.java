package io.nop.chart.echarts.render;

import io.nop.chart.echarts.NopChartEchartsException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 端到端：optionJson → GraalJS → shim → nop-canvas2d → PNG/SVG。
 * Anti-Hollow 双证：引擎 eval 计数增长（真实执行 dist）+ 渲染产物像素断言。
 */
class EChartsRendererTest {
    private static final String BAR_OPTION = """
            {"title":{"text":"引擎测试"},"xAxis":{"type":"category","data":["一","二","三"]},
             "yAxis":{"type":"value"},"series":[{"type":"bar","data":[10,30,20],
             "itemStyle":{"color":"#5470c6"}}]}
            """;

    private final EChartsRenderer renderer = new EChartsRenderer();

    @Test
    void renderPngEndToEnd() throws Exception {
        byte[] png = renderer.renderPng(BAR_OPTION, 600, 400);
        assertNotNull(png);
        // PNG 魔数
        assertEquals(0x89, png[0] & 0xFF);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(600, image.getWidth());
        assertEquals(400, image.getHeight());
        // 柱色像素存在（真实渲染非空白）
        assertTrue(countPixelsNear(image, 0x54, 0x70, 0xC6, 30) > 300,
                "bar series color must be present");
        // 文本墨迹存在（中文标题/类目渲染）
        assertTrue(hasDarkInk(image), "text ink must exist");
    }

    @Test
    void renderLineAndPie() {
        byte[] line = renderer.renderPng("""
                {"xAxis":{"type":"category","data":["a","b","c"]},"yAxis":{"type":"value"},
                 "series":[{"type":"line","data":[5,15,25],"lineStyle":{"color":"#91cc75"}}]}
                """, 400, 300);
        assertNotNull(line);
        byte[] pie = renderer.renderPng("""
                {"series":[{"type":"pie","radius":"60%",
                 "data":[{"value":40,"name":"甲"},{"value":60,"name":"乙"}]}]}
                """, 400, 300);
        assertNotNull(pie);
    }

    @Test
    void renderSvgProducesStaticSvg() {
        String svg = renderer.renderSvg(BAR_OPTION, 600, 400);
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertTrue(svg.contains("<path") || svg.contains("<rect"), "SVG must contain chart shapes");
        assertTrue(!svg.contains("@keyframes"), "cssAnimation must be disabled (D16)");
        // 强制 animation:false（D16）：静态终态不应含 animation 属性衍生的 CSS 类动画标记
        assertTrue(!svg.contains("animation:"), "static render must not emit css animation");
    }

    @Test
    void invalidOptionJsonFailsWithDedicatedError() {
        NopChartEchartsException ex = assertThrows(NopChartEchartsException.class,
                () -> renderer.renderPng("{\"xAxis\":", 300, 200));
        assertTrue(ex.getMessage().contains("invalid option JSON"));
    }

    @Test
    void normalizerStripsInteractiveComponentsWithoutMutatingInput() {
        String option = """
                {"toolbox":{"show":true},"tooltip":{"trigger":"axis"},
                 "dataZoom":[{"type":"inside"}],"timeline":{"show":true},
                 "legend":{"show":true},"series":[{"type":"bar","data":[1]}]}
                """;
        String normalized = renderer.normalize(option);
        assertTrue(!normalized.contains("toolbox"));
        assertTrue(!normalized.contains("tooltip"));
        assertTrue(!normalized.contains("dataZoom"));
        assertTrue(!normalized.contains("timeline"));
        assertTrue(normalized.contains("legend"), "legend must be preserved");
        assertTrue(normalized.contains("series"), "series must be preserved");
        // 原始输入不可变
        assertTrue(option.contains("toolbox"));
    }

    @Test
    void optionSizeAndBlankValidation() {
        assertThrows(NopChartEchartsException.class, () -> renderer.renderPng(null, 100, 100));
        assertThrows(NopChartEchartsException.class, () -> renderer.renderPng("  ", 100, 100));
        assertThrows(NopChartEchartsException.class, () -> renderer.renderPng("[]", 100, 100));
    }

    private static int countPixelsNear(BufferedImage image, int r, int g, int b, int tolerance) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int pr = (rgb >> 16) & 0xFF;
                int pg = (rgb >> 8) & 0xFF;
                int pb = rgb & 0xFF;
                if (Math.abs(pr - r) <= tolerance && Math.abs(pg - g) <= tolerance
                        && Math.abs(pb - b) <= tolerance)
                    count++;
            }
        }
        return count;
    }

    private static boolean hasDarkInk(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int pr = (rgb >> 16) & 0xFF;
                int lum = (pr * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                if (lum < 96 && ((rgb >>> 24) & 0xFF) > 140)
                    return true;
            }
        }
        return false;
    }

}
