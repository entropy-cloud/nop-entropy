package io.nop.chart.export;

import io.nop.excel.resolver.ExcelCellRefResolver;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExcelAllChartsExporter extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseAndSaveBarChart() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-all-charts.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        List<ExcelSheet> sheets = wk.getSheets();
        assertTrue(sheets != null && !sheets.isEmpty(), "Workbook should have at least one sheet");

        File resultDir = getTargetFile("result");
        resultDir.mkdirs();

        // 创建默认渲染器注册表
        ChartTypeRendererRegistry registry = ChartTypeRendererRegistry.getDefault();
        ChartExporter exporter = new ChartExporter(registry);


        for (ExcelSheet sheet : sheets) {
            // 创建Excel单元格引用解析器
            ExcelCellRefResolver resolver = new ExcelCellRefResolver(wk, sheet);
            List<ExcelChartModel> charts = sheet.getCharts();
            if (charts == null || charts.isEmpty())
                continue;

            // 导出每个图表
            for (int i = 0; i < charts.size(); i++) {
                ExcelChartModel chart = charts.get(i);
                File outputFile = new File(resultDir, sheet.getName() + "_chart_" + i + "_" + chart.getName() + ".png");
                exporter.exportToFile(outputFile, chart, resolver);
            }
        }
    }
}

