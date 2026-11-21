package io.nop.chart.export;

import io.nop.api.core.exceptions.NopException;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartModel;
import io.nop.excel.resolver.ICellRefResolver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2586 focused pins：ChartExporter 画布乘积上限守卫 + 超时参数化（不再写引擎全局态）。
 */
public class TestChartExporterGuards {

    static ICellRefResolver stubResolver() {
        return (ICellRefResolver) Proxy.newProxyInstance(
                TestChartExporterGuards.class.getClassLoader(),
                new Class<?>[]{ICellRefResolver.class},
                (proxy, method, args) -> {
                    Class<?> rt = method.getReturnType();
                    if (rt == List.class)
                        return java.util.Collections.emptyList();
                    return null;
                });
    }

    @Test
    public void testCanvasPixelProductCap() {
        ChartExporter exporter = new ChartExporter();
        ChartModel model = new ChartModel();
        model.setType(ChartType.BAR);
        ChartExportOptions options = new ChartExportOptions();
        options.setFormat("png");
        options.setWidth(5000);
        options.setHeight(5000); // 25M px > 16M cap

        NopException ex = assertThrows(NopException.class,
                () -> exporter.exportToStream(new ByteArrayOutputStream(), model, stubResolver(), options));
        assertNotNull(ex);
        assertTrue(String.valueOf(ex.getMessage()).contains("exceeds limit"),
                "cap error must carry limit reason, got: " + ex.getMessage());
    }
}
