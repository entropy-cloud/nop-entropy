package io.nop.excel.renderer;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.IExcelSheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry three-state discovery (D42) and the visual-overlay merge semantics
 * (plan 2365 Phase 1): provider present renders chart layers, absent leaves the
 * output unchanged, provider failures surface (D43).
 */
class ChartImageProviderRegistryTest {

    @AfterEach
    void cleanup() {
        ChartImageProviderRegistry.unregister();
        if (BeanContainer.isInitialized())
            BeanContainer.registerInstance(null);
    }

    @Test
    void discoveryThreeStates() {
        // 1. no static registration + no initialized container -> null (silent skip)
        ChartImageProviderRegistry.unregister();
        if (BeanContainer.isInitialized())
            BeanContainer.registerInstance(null);
        assertNull(ChartImageProviderRegistry.getProvider(), "double-null must resolve to null (D42)");

        // 2. static registration wins
        IChartImageProvider provider = sheet -> new ArrayList<>();
        ChartImageProviderRegistry.register(provider);
        assertEquals(provider, ChartImageProviderRegistry.getProvider());
        ChartImageProviderRegistry.unregister();
        assertNull(ChartImageProviderRegistry.getProvider());

        // 3. IoC fallback: empty container yields null via tryGetBeanByType (R1 B1)
        io.nop.api.core.ioc.StaticBeanContainer empty = new io.nop.api.core.ioc.StaticBeanContainer();
        BeanContainer.registerInstance(empty);
        assertNull(ChartImageProviderRegistry.getProvider(),
                "initialized container without provider bean -> null, not throw (R1 B1)");
    }

    @Test
    void providerFailureSurfaces() {
        ChartImageProviderRegistry.register(sheet -> {
            throw new IllegalStateException("render exploded");
        });
        TestSheet sheet = sheet(true, new ArrayList<>());
        assertThrows(IllegalStateException.class,
                () -> HtmlSheetVisualRenderer.renderVisualOverlays(sheet, "s1", new StringWriter()),
                "provider failure must not be swallowed (D43)");
    }

    @Test
    void providerPresentAddsChartLayer() throws Exception {
        ChartImageProviderRegistry.register(sheet -> {
            ExcelImage img = new ExcelImage();
            img.setImgType("png");
            img.setData(io.nop.commons.bytes.ByteString.of(new byte[]{1, 2, 3}));
            img.setLeft(10);
            img.setTop(20);
            img.setWidth(100);
            img.setHeight(50);
            return Arrays.asList(img);
        });
        TestSheet sheet = sheet(true, new ArrayList<>());
        StringWriter out = new StringWriter();
        HtmlSheetVisualRenderer.renderVisualOverlays(sheet, "s1", out);
        String html = out.toString();
        assertTrue(html.contains("data:image/png;base64"), "chart rendered as png data url: " + html);
        assertTrue(html.contains("min-height:70"), "chart contributes to overlay extent");
    }

    @Test
    void providerAbsentLeavesOutputUnchanged() throws Exception {
        ChartImageProviderRegistry.unregister();
        if (BeanContainer.isInitialized())
            BeanContainer.registerInstance(null);

        TestSheet withImages = sheet(false, new ArrayList<>(List.of(image(1))));
        StringWriter baseline = new StringWriter();
        HtmlSheetVisualRenderer.renderVisualOverlays(withImages, "s1", baseline);

        TestSheet withCharts = sheet(true, new ArrayList<>(List.of(image(1))));
        StringWriter after = new StringWriter();
        HtmlSheetVisualRenderer.renderVisualOverlays(withCharts, "s1", after);

        // charts exist (hasChart=true) but no provider: identical output to baseline
        assertEquals(baseline.toString(), after.toString(),
                "provider absence must not change the output (D43)");
        assertTrue(baseline.toString().contains("data:image/png"), "sheet images still render");
    }

    @Test
    void providerPresentWithoutChartsKeepsImagesOnly() throws Exception {
        ChartImageProviderRegistry.register(sheet -> new ArrayList<>());
        TestSheet sheet = sheet(true, new ArrayList<>(List.of(image(1))));
        StringWriter out = new StringWriter();
        HtmlSheetVisualRenderer.renderVisualOverlays(sheet, "s1", out);
        assertTrue(out.toString().contains("data:image/png"), "images still render when charts empty");
    }

    private ExcelImage image(int index) {
        ExcelImage img = new ExcelImage();
        img.setImgType("png");
        img.setData(io.nop.commons.bytes.ByteString.of(new byte[]{(byte) index}));
        img.setLeft(index);
        img.setTop(index);
        img.setWidth(10);
        img.setHeight(10);
        return img;
    }

    /**
     * Proxy stub: IExcelSheet has many members; only hasChart/getImages/getCharts
     * and the layout getters used by the renderer are meaningful here.
     */
    static TestSheet sheet(boolean hasChart, List<ExcelImage> images) {
        List<ExcelImage> imgs = images;
        return (TestSheet) java.lang.reflect.Proxy.newProxyInstance(
                ChartImageProviderRegistryTest.class.getClassLoader(),
                new Class<?>[]{IExcelSheet.class, TestSheet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hasChart":
                            return hasChart;
                        case "getImages":
                            return imgs;
                        case "getCharts":
                            return new ArrayList<>();
                        case "hasImage":
                            return imgs != null && !imgs.isEmpty();
                        case "getName":
                            return "test";
                        case "getDefaultRowHeight":
                            return 13.5f;
                        case "getDefaultColumnWidth":
                            return 60.0f;
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class)
                                return false;
                            if (Number.class.isAssignableFrom(rt) || rt.isPrimitive())
                                return 0;
                            return null;
                    }
                });
    }

    interface TestSheet extends IExcelSheet {
    }
}
