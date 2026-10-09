package io.nop.chart.echarts.engine;

import io.nop.chart.echarts.NopChartEchartsException;
import io.nop.chart.echarts.bridge.EchartsBridge;
import io.nop.canvas2d.TextMeasurer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GraalJS 引擎（plan 2362 D13/D14/D18）：
 * ThreadLocal Context 复用（共享 Engine）；echarts.js 源缓存且每 Context 只 eval 一次；
 * 裸环境红线（无 window/document/navigator/self）；timer 泵至静默；渲染超时 interrupt +
 * 污染 Context 重建；渲染开始清 timer 队列。
 */
public class EchartsEngine {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(EchartsEngine.class);
    private static final String ECHARTS_RESOURCE = "META-INF/resources/_static/echarts/6.1.0/dist/echarts.js";
    private static final String BRIDGE_RESOURCE = "echarts/echarts-canvas-bridge.js";
    private static final String JS_LANG = "js";

    private static volatile EchartsEngine instance = new EchartsEngine();

    public static EchartsEngine get() {
        return instance;
    }

    /** D14/D23 配置面：静态单例吃不到 NopIoC 注入，IoC bean 接线归 WI7-8 报表装配。 */
    public static void configure(TextMeasurer textMeasurer, long renderTimeoutMs, int timerPumpMaxRounds) {
        EchartsEngine engine = instance;
        engine.textMeasurer = textMeasurer;
        engine.renderTimeoutMs = renderTimeoutMs;
        engine.timerPumpMaxRounds = timerPumpMaxRounds;
    }

    private final Engine sharedEngine = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();
    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "echarts-render-timeout");
                t.setDaemon(true);
                return t;
            });
    private final ThreadLocal<Context> contextHolder = new ThreadLocal<>();
    private final AtomicLong echartsEvalCount = new AtomicLong();
    private final AtomicLong renderCount = new AtomicLong();
    private final AtomicLong contextRebuildCount = new AtomicLong();

    private volatile TextMeasurer textMeasurer = new TextMeasurer();
    private volatile long renderTimeoutMs = 30_000;
    private volatile int timerPumpMaxRounds = 16;
    private volatile String echartsSource;
    private volatile String bridgeSource;

    private EchartsEngine() {
    }

    public long getRenderTimeoutMs() {
        return renderTimeoutMs;
    }

    public long getEchartsEvalCount() {
        return echartsEvalCount.get();
    }

    public long getRenderCount() {
        return renderCount.get();
    }

    public long getContextRebuildCount() {
        return contextRebuildCount.get();
    }

    /**
     * canvas 渲染：装配桥与 echarts，setOption 后 pump 到静默，返回 root 画布图像。
     */
    public BufferedImageHolder renderCanvas(String normalizedOptionJson, int width, int height) {
        return renderCanvasSeam(normalizedOptionJson, width, height, null);
    }

    /**
     * D14 包私有测试缝：pre-render JS 注入（仅 io.nop.chart.echarts.engine 包内测试可见，
     * 生产恒走 3 参重载）——allowAllAccess Context 不得暴露任意 JS 注入公共面。
     */
    BufferedImageHolder renderCanvasSeam(String normalizedOptionJson, int width, int height,
                                         String preRenderScript) {
        return renderCanvasSeam(normalizedOptionJson, width, height, preRenderScript, false);
    }

    /**
     * keepAlive=true：跳过 dispose-nulling（测试在 dispose 前经 evaluateForTest 断言 JS 状态），
     * 测试结束须自行调 disposeForTest。
     */
    BufferedImageHolder renderCanvasSeam(String normalizedOptionJson, int width, int height,
                                         String preRenderScript, boolean keepAlive) {
        Context context = contextForCurrentThread();
        renderCount.incrementAndGet();
        ScheduledFuture<?> timeoutTask = scheduleTimeout(context);
        try {
            Value bindings = context.getBindings(JS_LANG);
            EchartsBridge bridge = new EchartsBridge(new EchartsBridge.TextMeasurerHost(textMeasurer), timerPumpMaxRounds);
            bindings.putMember("__bridge", bridge);
            String script = renderScript(normalizedOptionJson, width, height, "canvas", preRenderScript);
            context.eval(JS_LANG, script);
            int pumped = bridge.drainTimers();
            Value image = context.eval(JS_LANG, "globalThis.__activeRoot.__canvasBackend.getImage()");
            return new BufferedImageHolder(image.asHostObject(), pumped, bridge.getImageRequests());
        } catch (PolyglotException e) {
            closeCurrentContext();
            if (e.isInterrupted() || e.isCancelled())
                throw new NopChartEchartsException(
                        "echarts render timed out after " + renderTimeoutMs + "ms", e);
            throw new NopChartEchartsException("echarts JS execution failed: " + e.getMessage(), e);
        } finally {
            // D21：instances$1 主注册表强引用 chart（dist:30282）——每渲染必须 dispose 防泄漏
            if (!keepAlive)
                disposeActiveChart(context);
            timeoutTask.cancel(false);
        }
    }

    /**
     * SVG 渲染（D16）：ssr:true + 强制 animation:false + renderToSVGString。
     */
    public String renderSvg(String normalizedOptionJson, int width, int height) {
        return renderSvgSeam(normalizedOptionJson, width, height, null);
    }

    /**
     * D14 包私有测试缝（同 renderCanvasSeam）。
     */
    String renderSvgSeam(String normalizedOptionJson, int width, int height, String preRenderScript) {
        Context context = contextForCurrentThread();
        renderCount.incrementAndGet();
        ScheduledFuture<?> timeoutTask = scheduleTimeout(context);
        try {
            Value bindings = context.getBindings(JS_LANG);
            EchartsBridge bridge = new EchartsBridge(new EchartsBridge.TextMeasurerHost(textMeasurer), timerPumpMaxRounds);
            bindings.putMember("__bridge", bridge);
            String script = renderScript(normalizedOptionJson, width, height, "svg", preRenderScript);
            context.eval(JS_LANG, script);
            bridge.drainTimers();
            return context.eval(JS_LANG, "globalThis.__activeChart.renderToSVGString({cssAnimation: false})")
                    .asString();
        } catch (PolyglotException e) {
            closeCurrentContext();
            if (e.isInterrupted() || e.isCancelled())
                throw new NopChartEchartsException(
                        "echarts render timed out after " + renderTimeoutMs + "ms", e);
            throw new NopChartEchartsException("echarts JS execution failed: " + e.getMessage(), e);
        } finally {
            disposeActiveChart(context);
            timeoutTask.cancel(false);
        }
    }

    private String renderScript(String normalizedOptionJson, int width, int height, String renderer,
                                String preRenderScript) {
        StringBuilder sb = new StringBuilder();
        sb.append("__bridgeSetupPlatform();\n");
        if (preRenderScript != null && !preRenderScript.isEmpty())
            sb.append(preRenderScript).append('\n');
        sb.append("__bridgeInitChart(").append(width).append(", ").append(height)
                .append(", '").append(renderer).append("', ");
        sb.append(jsonAsJsExpression(normalizedOptionJson));
        sb.append(");\n");
        return sb.toString();
    }

    /**
     * D20：normalized JSON 以字符串字面量内嵌，JS 侧 JSON.parse 得纯 JS 对象
     * （JSON.parse 不触发原型污染；字面量转义覆盖斜杠/引号/控制字符/U+2028/2029）。
     */
    static String jsonAsJsExpression(String normalizedJson) {
        StringBuilder sb = new StringBuilder(normalizedJson.length() + 16);
        sb.append("JSON.parse(\"");
        for (int i = 0; i < normalizedJson.length(); i++) {
            char ch = normalizedJson.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\u2028':
                    sb.append("\\u2028");
                    break;
                case '\u2029':
                    sb.append("\\u2029");
                    break;
                default:
                    sb.append(ch);
            }
        }
        sb.append("\")");
        return sb.toString();
    }

    private Context contextForCurrentThread() {
        Context context = contextHolder.get();
        if (context != null)
            return context;
        context = buildContext();
        contextHolder.set(context);
        return context;
    }

    private Context buildContext() {
        Context context = Context.newBuilder(JS_LANG)
                .engine(sharedEngine)
                .allowAllAccess(true)
                .build();
        // 默认桥：shim 级测试（evaluateForTest）可独立于渲染流程使用；渲染时按次换新桥
        context.getBindings(JS_LANG).putMember("__bridge",
                new EchartsBridge(new EchartsBridge.TextMeasurerHost(textMeasurer), timerPumpMaxRounds));
        context.eval(JS_LANG, bridgeSource());
        context.eval(JS_LANG, echartsSource());
        echartsEvalCount.incrementAndGet();
        return context;
    }

    /**
     * D21：渲染结束（含异常路径）dispose 当前 chart；泄漏回归经 {@link #evaluateForTest} 断言。
     */
    private void disposeActiveChart(Context context) {
        try {
            context.eval(JS_LANG,
                    "if (globalThis.__activeChart) { globalThis.__activeChart.dispose(); "
                            + "globalThis.__activeChart = null; globalThis.__activeRoot = null; }");
        } catch (PolyglotException e) {
            // 污染 Context（超时路径）下 dispose 本身会失败——Context 已整体关闭重建
            LOG.debug("dispose skipped on poisoned context", e);
        }
    }

    /**
     * 包私有测试评估缝（D14）：供引擎层测试做红线/泄漏/状态断言，不进公共 API。
     */
    String evaluateForTest(String js) {
        return contextForCurrentThread().eval(JS_LANG, js).toString();
    }

    /**
     * keepAlive 渲染后的手动 dispose（测试收口）。
     */
    void disposeForTest() {
        Context context = contextForCurrentThread();
        disposeActiveChart(context);
    }

    /**
     * 包私有测试缝：渲染并返回 image 请求计数（D18 loadImage defer 可观测）。
     */
    int renderCanvasForTest(String optionJson, int width, int height) {
        return renderCanvasSeam(optionJson, width, height, null).imageRequests();
    }

    private void closeCurrentContext() {
        Context context = contextHolder.get();
        if (context != null) {
            contextHolder.remove();
            context.close(true);
            contextRebuildCount.incrementAndGet();
        }
    }

    private ScheduledFuture<?> scheduleTimeout(Context context) {
        return timeoutScheduler.schedule(() -> {
            try {
                // interrupt(Duration) 为 cancel+close 语义（25.2.4 仅此签名）；Duration 是「等待安全点」
                // 上限——ZERO 会不中断直接抛 TimeoutException，须给足等待（5s）再强制中断
                context.interrupt(java.time.Duration.ofSeconds(5));
            } catch (TimeoutException e) {
                LOG.debug("interrupt timed out waiting for guest safe point", e);
            }
        }, renderTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private String echartsSource() {
        String source = echartsSource;
        if (source == null) {
            synchronized (this) {
                if (echartsSource == null)
                    echartsSource = readClasspathResource(ECHARTS_RESOURCE);
            }
            source = echartsSource;
        }
        return source;
    }

    private String bridgeSource() {
        String source = bridgeSource;
        if (source == null) {
            synchronized (this) {
                if (bridgeSource == null)
                    bridgeSource = readClasspathResource(BRIDGE_RESOURCE);
            }
            source = bridgeSource;
        }
        return source;
    }

    private static String readClasspathResource(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null)
                throw new NopChartEchartsException("classpath resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopChartEchartsException("failed to read classpath resource: " + path, e);
        }
    }

    /**
     * 渲染产物：root 画布图像 + 泵/图像请求统计（供测试断言）。
     */
    public static final class BufferedImageHolder {
        private final java.awt.image.BufferedImage image;
        private final int pumpedTimers;
        private final int imageRequests;

        public BufferedImageHolder(Object image, int pumpedTimers, int imageRequests) {
            this.image = (java.awt.image.BufferedImage) image;
            this.pumpedTimers = pumpedTimers;
            this.imageRequests = imageRequests;
        }

        public java.awt.image.BufferedImage image() {
            return image;
        }

        public int pumpedTimers() {
            return pumpedTimers;
        }

        public int imageRequests() {
            return imageRequests;
        }
    }
}
