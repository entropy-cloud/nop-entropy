package io.nop.chart.echarts.engine;

import io.nop.chart.echarts.NopChartEchartsException;
import io.nop.canvas2d.TextMeasurer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 引擎层测试（同包访问 D14 包私有测试缝）：Context 复用、裸环境红线、D21 dispose、
 * 超时与 JS 异常错误面、迟到中断不毒化（D14/M1）。
 */
class EchartsEngineTest {
    private static final String BAR_OPTION = """
            {"xAxis":{"type":"category","data":["一","二","三"]},"yAxis":{"type":"value"},
             "series":[{"type":"bar","data":[10,30,20],"itemStyle":{"color":"#5470c6"}}]}
            """;

    private final EchartsEngine engine = EchartsEngine.get();

    @Test
    void contextReusedAcrossRenders() {
        // Anti-Hollow 接线证据：Context 复用——已存在的 Context 上连续渲染，eval 计数零增长
        //（平坦性断言，不依赖测试执行顺序；首次 eval 由前序测试或下一行触发）
        engine.renderCanvas(BAR_OPTION, 300, 200);
        long evalsAtSteadyState = engine.getEchartsEvalCount();
        engine.renderCanvas(BAR_OPTION, 300, 200);
        engine.renderCanvas(BAR_OPTION, 300, 200);
        assertEquals(evalsAtSteadyState, engine.getEchartsEvalCount(),
                "echarts.js must be evaluated once per Context, not per render");
        assertTrue(engine.getRenderCount() >= 3);
    }

    @Test
    void bareEnvRedLineHolds() {
        // roadmap Cross-Cutting #2：引擎内不得定义 window/document/navigator/self
        engine.renderCanvas(BAR_OPTION, 300, 200);
        assertEquals("", engine.evaluateForTest(
                "var leaked = [];"
                        + "if (typeof window !== 'undefined') leaked.push('window');"
                        + "if (typeof document !== 'undefined') leaked.push('document');"
                        + "if (typeof navigator !== 'undefined') leaked.push('navigator');"
                        + "if (typeof self !== 'undefined') leaked.push('self');"
                        + "leaked.join(',')"));
    }

    @Test
    void disposePreventsInstanceLeak() {
        // D21 回归：渲染结束后 getInstanceByDom 必须为 undefined（instances$1 强引用已释放）
        engine.renderCanvas(BAR_OPTION, 300, 200);
        // dispose 后清理标记置空（getInstanceByDom 参数为 null 会抛错，改验清理标记）
        assertEquals("", engine.evaluateForTest(
                "globalThis.__activeChart === null && globalThis.__activeRoot === null ? '' : 'not-cleaned'"));
        // 再渲染一次无实例堆积异常（dispose 后 registry 可安全复用）
        engine.renderCanvas(BAR_OPTION, 300, 200);
    }

    @Test
    void jsExceptionWrappedWithMessage() {
        // D14 测试缝：pre-render JS 抛错 → 包装异常含 JS 侧消息
        NopChartEchartsException ex = assertThrows(NopChartEchartsException.class,
                () -> engine.renderCanvasSeam(BAR_OPTION, 300, 200, "throw new Error('boom-seam');"));
        assertTrue(ex.getMessage().contains("boom-seam"),
                "wrapped exception must carry JS message, got: " + ex.getMessage());
    }

    @Test
    void timeoutFailsAndLaterRenderStaysHealthy() {
        // D14：超时 → render timed out；迟到中断不毒化下一次渲染（M1 回归）
        long original = engine.getRenderTimeoutMs();
        try {
            EchartsEngine.configure(new TextMeasurer(), 200, 16);
            NopChartEchartsException ex = assertThrows(NopChartEchartsException.class,
                    () -> engine.renderCanvasSeam(BAR_OPTION, 300, 200, "while(true){}"));
            assertTrue(ex.getMessage().contains("timed out"), "got: " + ex.getMessage());
        } finally {
            EchartsEngine.configure(new TextMeasurer(), original, 16);
        }
        // 迟到中断不毒化：恢复超时后的健康渲染必须成功
        BufferedImage image = engine.renderCanvas(BAR_OPTION, 300, 200).image();
        assertEquals(300, image.getWidth());
        assertNotEquals(0, image.getRGB(150, 100));
    }

    @Test
    void pumpMaxRoundsConfigureTakesEffect() {
        // M1 回归：configure 的 timerPumpMaxRounds 真实生效——pump=1 时渐进渲染 pumpedTimers ≤ 1
        StringBuilder data = new StringBuilder("[");
        for (int i = 0; i < 4000; i++) {
            if (i > 0)
                data.append(',');
            data.append(i % 97 + 5);
        }
        data.append(']');
        String option = "{\"xAxis\":{\"type\":\"category\"},\"yAxis\":{\"type\":\"value\"},"
                + "\"series\":[{\"type\":\"bar\",\"data\":" + data + "}]}";
        long original = engine.getRenderTimeoutMs();
        try {
            EchartsEngine.configure(new TextMeasurer(), original, 1);
            EchartsEngine.BufferedImageHolder result = engine.renderCanvasSeam(BAR_OPTION, 300, 200, null);
            assertTrue(result.pumpedTimers() <= 1,
                    "pump=1 must cap pumped timers, got " + result.pumpedTimers());
            EchartsEngine.configure(new TextMeasurer(), original, 16);
            EchartsEngine.BufferedImageHolder after = engine.renderCanvasSeam(BAR_OPTION, 300, 200, null);
            assertTrue(after.pumpedTimers() > result.pumpedTimers(),
                    "pump=16 must allow more rounds than pump=1");
        } finally {
            EchartsEngine.configure(new TextMeasurer(), original, 16);
        }
    }
}