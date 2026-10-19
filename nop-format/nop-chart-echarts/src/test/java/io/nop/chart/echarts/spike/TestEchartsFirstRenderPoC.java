package io.nop.chart.echarts.spike;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2358 WI0 首图 PoC：JVM 内 GraalJS 裸环境加载 dist/echarts.js，渲染含中文标签的 bar PNG。
 * 验证：(1) 裸环境红线（无 window/document/navigator/self）；(2) GraalJS host-interop 可行；
 * (3) 中文字体在逻辑字体下可渲染；(4) 端到端 JS→canvas→PNG 路径真实连通。
 * spike 代码（本目录 + spike/*.js 资源）为一次性验证物，生产引擎归 WI4。
 */
class TestEchartsFirstRenderPoC {
    private static final String ECHARTS_RESOURCE = "META-INF/resources/_static/echarts/6.1.0/dist/echarts.js";
    private static final String SHIM_RESOURCE = "spike/echarts-bare-env.js";
    private static final String RENDER_SCRIPT_RESOURCE = "spike/render-bar-chart.js";
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    private static final int BAR_RGB_R = 0x54;
    private static final int BAR_RGB_G = 0x70;
    private static final int BAR_RGB_B = 0xC6;

    @Test
    void bareEnvRendersChineseBarPng() throws Exception {
        String echartsJs = readClasspathResource(ECHARTS_RESOURCE);
        String shimJs = readClasspathResource(SHIM_RESOURCE);
        String renderJs = readClasspathResource(RENDER_SCRIPT_RESOURCE);

        SpikeHosts.Console console = new SpikeHosts.Console();
        SpikeHosts.Timer timer = new SpikeHosts.Timer();
        SpikeHosts.ImageRegistry images = new SpikeHosts.ImageRegistry();
        SpikeHosts.BackendFactory factory = new SpikeHosts.BackendFactory();

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            var bindings = context.getBindings("js");
            bindings.putMember("__hostsConsole", console);
            bindings.putMember("__hostsTimer", timer);
            bindings.putMember("__hostsImage", images);
            bindings.putMember("__hostsBackendFactory", factory);
            bindings.putMember("__spikeEchartsSrc", echartsJs);

            context.eval("js", shimJs);
            context.eval("js", echartsJs);

            // 裸环境红线：宿主全局不得出现浏览器对象
            boolean bare = context.eval("js",
                    "typeof window === 'undefined' && typeof document === 'undefined' "
                            + "&& typeof navigator === 'undefined' && typeof self === 'undefined'").asBoolean();
            assertTrue(bare, "bare env must not define window/document/navigator/self");

            context.eval("js", renderJs);
            assertEquals(true, context.eval("js", "globalThis.__spikeChartReady === true").asBoolean());
            int drained = timer.drain(8);
            System.out.println("[echarts.spike] drained timers: " + drained);
        }

        assertTrue(images.getRequested().isEmpty(),
                "bar chart must not request images, got: " + images.getRequested());

        // 渲染发生在 zrender 的 layer canvas 上，取非白像素最多的后端即为图表画布
        SpikeCanvasBackend best = null;
        int bestNonWhite = -1;
        List<SpikeCanvasBackend> backends = factory.getBackends();
        for (int i = 0; i < backends.size(); i++) {
            SpikeCanvasBackend backend = backends.get(i);
            int nonWhite = backend.nonWhitePixelCount();
            System.out.println("[echarts.spike] backend#" + i + " size=" + backend.getWidth() + "x"
                    + backend.getHeight() + " nonWhite=" + nonWhite
                    + " barPixels=" + backend.countPixelsNear(BAR_RGB_R, BAR_RGB_G, BAR_RGB_B, 30)
                    + " darkPixels=" + backend.countDarkPixels(96));
            backend.savePng(Path.of("target", "wi0-layer-" + i + ".png").toFile());
            if (nonWhite > bestNonWhite) {
                bestNonWhite = nonWhite;
                best = backend;
            }
        }
        assertNotNull(best);
        assertEquals(WIDTH, best.getWidth());
        assertEquals(HEIGHT, best.getHeight());

        // 先落盘再断言，失败时可肉眼检查 target/wi0-layer-*.png
        Path out = Path.of("target", "wi0-first-render.png");
        best.savePng(out.toFile());
        assertTrue(Files.exists(out));
        var decoded = ImageIO.read(out.toFile());
        assertEquals(WIDTH, decoded.getWidth());
        assertEquals(HEIGHT, decoded.getHeight());

        assertTrue(bestNonWhite > 1000, "chart must paint content, nonWhite=" + bestNonWhite);

        int barPixels = best.countPixelsNear(BAR_RGB_R, BAR_RGB_G, BAR_RGB_B, 30);
        assertTrue(barPixels > 500, "bar series color #5470c6 must be present, barPixels=" + barPixels);

        int textPixels = best.countDarkPixels(96);
        assertTrue(textPixels > 50, "Chinese text must be rendered, darkPixels=" + textPixels);

        System.out.println("[echarts.spike] PNG written: " + out.toAbsolutePath()
                + " nonWhite=" + bestNonWhite + " barPixels=" + barPixels + " darkPixels=" + textPixels);
    }

    private static String readClasspathResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "classpath resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
