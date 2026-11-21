package io.nop.chart.echarts.bridge;

import io.nop.chart.echarts.NopChartEchartsException;
import io.nop.canvas2d.IImageData;
import io.nop.canvas2d.TextMeasurer;
import io.nop.canvas2d.ImageData;
import io.nop.canvas2d.Java2DCanvas;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * GraalJS ↔ nop-canvas2d 渲染桥（每个渲染实例一个）：
 * 画布工厂（root/layer/measure）、console/timer 宿主、loadImage defer 登记、timer 泵。
 * 桥的方法被 JS shim 以 host 方法调用（allowAllAccess），数据形态值一律以纯 JS 对象跨越边界
 * （WI0 interop 约束：Java 对象属性读不映射 getter）。
 */
public class EchartsBridge {
    private static final Logger LOG = LoggerFactory.getLogger(EchartsBridge.class);

    private final List<Java2DCanvas> canvases = new ArrayList<>();
    private final List<Value> pendingTimers = new ArrayList<>();
    private final TextMeasurerHost measurer;
    private final int maxDrainRounds;

    private int timerCancelled;
    private int imageRequests;

    public EchartsBridge(TextMeasurerHost measurer, int maxDrainRounds) {
        this.measurer = measurer;
        this.maxDrainRounds = maxDrainRounds;
    }

    /**
     * 画布工厂：root/layer/隐藏测量画布统一走这里，全部登记便于渲染结束后统一可达。
     */
    public Java2DCanvas createCanvas(int width, int height) {
        Java2DCanvas canvas = new Java2DCanvas(width, height, measurer.textMeasurer());
        canvases.add(canvas);
        return canvas;
    }

    /**
     * D22（复核修订）：异值赋值重建底层缓冲（浏览器 resize 清空语义）——root 路径恒同值不受影响；
     * heatmap brush canvas（dist:69663）与 decal canvas（dist:27942）会被 zrender 合法赋异值。
     * 返回换绑后的新 backend（shim 持有包装引用惰性取用）。
     */
    public Java2DCanvas resizeCanvas(Java2DCanvas canvas, double width, double height) {
        if (canvas.getWidth() == (int) width && canvas.getHeight() == (int) height)
            return canvas;
        if (width <= 0 || height <= 0)
            throw new NopChartEchartsException("canvas resize to non-positive size: " + width + "x" + height);
        Java2DCanvas next = new Java2DCanvas((int) width, (int) height, measurer.textMeasurer());
        // plan 2586：resize 后旧画布从登记表移除（zrender 已换绑），避免渲染期画布累积
        int idx = canvases.indexOf(canvas);
        if (idx >= 0)
            canvases.set(idx, next);
        else
            canvases.add(next);
        return next;
    }

    public boolean isCanvas(Object value) {
        return value instanceof Java2DCanvas;
    }

    public Java2DCanvas asCanvas(Object value) {
        if (!(value instanceof Java2DCanvas canvas))
            throw new NopChartEchartsException("expected a bridge canvas, got: " + value);
        return canvas;
    }

    // ----- console -----

    public void logInfo(String message) {
        LOG.info("[echarts] {}", message);
    }

    public void logWarn(String message) {
        LOG.warn("[echarts] {}", message);
    }

    public void logError(String message) {
        LOG.error("[echarts] {}", message);
    }

    // ----- timers（D18） -----

    public int scheduleTimer(Value fn, double delay) {
        pendingTimers.add(fn);
        return pendingTimers.size();
    }

    public void cancelTimer(Object id) {
        timerCancelled++;
    }

    /**
     * progressive 泵：执行排队回调至静默（上限由引擎配置注入，M1 修复：configure 生效）。
     * guest 回调抛出的异常原样向上传播。
     */
    public int drainTimers() {
        int executed = 0;
        for (int round = 0; round < maxDrainRounds && !pendingTimers.isEmpty(); round++) {
            List<Value> batch = new ArrayList<>(pendingTimers);
            pendingTimers.clear();
            for (Value fn : batch) {
                fn.execute();
                executed++;
            }
        }
        return executed;
    }

    // ----- loadImage defer（D18） -----

    public int getImageRequests() {
        return imageRequests;
    }

    public void requestImage(String src) {
        imageRequests++;
        LOG.warn("[echarts] static render ignores image request: {}", src);
    }

    /**
     * measureText 平台注入的后端：返回宽度（JS 侧包装为纯 JS 对象 {width}）。
     */
    public double measureWidth(String text, String cssFont) {
        return measurer.measureWidth(text, cssFont);
    }

    /**
     * D17：IImageData（int[] ARGB）→ JS 友好的 int[] RGBA（0-255，无符号）——数据形态值以纯
     * JS 数组跨越边界（WI0 interop 约束）。
     */
    public int[] imageDataToRgba(Object imageData) {
        if (!(imageData instanceof IImageData data))
            throw new NopChartEchartsException("expected IImageData, got: " + imageData);
        int[] argb = data.getData();
        int[] rgba = new int[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            int v = argb[i];
            int o = i * 4;
            rgba[o] = (v >> 16) & 0xFF;
            rgba[o + 1] = (v >> 8) & 0xFF;
            rgba[o + 2] = v & 0xFF;
            rgba[o + 3] = (v >>> 24) & 0xFF;
        }
        return rgba;
    }

    /**
     * D17：JS 侧 RGBA 数组 → putImageData 整块替换（绕过全部状态）。
     */
    public void putImageDataRgba(Java2DCanvas canvas, Value rgbaArray, int width, int height,
                                 double dx, double dy) {
        int len = (int) rgbaArray.getArraySize();
        if (len != width * height * 4)
            throw new NopChartEchartsException("putImageData data length mismatch: " + len
                    + " != " + width + "*" + height + "*4");
        int[] argb = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int r = rgbaArray.getArrayElement(i * 4L).asInt();
            int g = rgbaArray.getArrayElement(i * 4L + 1).asInt();
            int b = rgbaArray.getArrayElement(i * 4L + 2).asInt();
            int a = rgbaArray.getArrayElement(i * 4L + 3).asInt();
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        canvas.putImageData(new ImageData(width, height, argb), dx, dy);
    }

    /**
     * TextMeasurer 宿主视图（D23 字体配置面）：引擎持有的度量器注入桥。
     */
    public record TextMeasurerHost(TextMeasurer textMeasurer) {
        public double measureWidth(String text, String cssFont) {
            return textMeasurer.measure(text, cssFont).getWidth();
        }
    }
}
