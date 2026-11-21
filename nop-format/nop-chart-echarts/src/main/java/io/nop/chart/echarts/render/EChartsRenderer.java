package io.nop.chart.echarts.render;

import io.nop.chart.echarts.NopChartEchartsException;
import io.nop.core.lang.json.JsonTool;
import io.nop.chart.echarts.engine.EchartsEngine;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * ECharts 静态渲染统一入口（plan 2362）：option JSON → PNG（byte[]）/SVG（String）。
 * Java 侧 Jackson 校验 + Normalizer 剥离 + 重序列化；JS 侧 JSON.parse 得纯 JS 对象（D20）。
 */
public class EChartsRenderer {
    private static final Set<String> STRIPPED_COMPONENTS = Set.of("toolbox", "tooltip", "brush", "dataZoom", "timeline");

    private volatile EchartsEngine engine = EchartsEngine.get();

    public void setEngine(EchartsEngine engine) {
        this.engine = engine;
    }

    /**
     * 渲染为 PNG（D19：编码字节在 Renderer 内产出，WI7 dataURL 与 WI8 PdfImageHelper 直接消费）。
     * 超时取引擎默认值。
     */
    public byte[] renderPng(String optionJson, int width, int height) {
        BufferedImage image = renderCanvas(normalize(optionJson), width, height, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new NopChartEchartsException("PNG encoding failed", e);
        }
        return out.toByteArray();
    }

    /**
     * 请求级超时 PNG 渲染（plan 2586：超时只作用于本次调用，timeoutMs≤0 用引擎默认）。
     */
    public byte[] renderPng(String optionJson, int width, int height, long timeoutMs) {
        BufferedImage image = renderCanvas(normalize(optionJson), width, height, timeoutMs);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new NopChartEchartsException("PNG encoding failed", e);
        }
        return out.toByteArray();
    }

    /**
     * 渲染为 SVG 字符串（D16：ssr:true + 强制 animation:false + cssAnimation:false）。
     */
    public String renderSvg(String optionJson, int width, int height) {
        return renderSvg(optionJson, width, height, 0);
    }

    /**
     * 请求级超时 SVG 渲染。
     */
    public String renderSvg(String optionJson, int width, int height, long timeoutMs) {
        return engine.renderSvg(normalize(optionJson), width, height, timeoutMs);
    }

    private BufferedImage renderCanvas(String normalizedOptionJson, int width, int height, long timeoutMs) {
        return engine.renderCanvas(normalizedOptionJson, width, height, timeoutMs).image();
    }

    /**
     * D15：剥离顶层交互组件（toolbox/tooltip/brush/dataZoom/timeline，含数组形态）；
     * 深拷贝语义——不修改调用方输入；坏 JSON 抛 {@link NopChartEchartsException}（独立错误面，D20）。
     */
    public String normalize(String optionJson) {
        if (optionJson == null || optionJson.isBlank())
            throw new NopChartEchartsException("option JSON must not be null or blank");
        Map<String, Object> root;
        try {
            root = JsonTool.parseMap(optionJson);
        } catch (Exception e) {
            throw new NopChartEchartsException("invalid option JSON: " + e.getMessage(), e);
        }
        STRIPPED_COMPONENTS.forEach(root::remove);
        return JsonTool.stringify(root);
    }
}
