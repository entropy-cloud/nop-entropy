package io.nop.ooxml.xlsx.parse;

import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBarChartParser extends BaseTestCase {
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
        IResource resource = new ClassPathResource("classpath:xlsx/test-bar-chart.xlsx");
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        List<ExcelSheet> sheets = wk.getSheets();
        assertTrue(sheets != null && !sheets.isEmpty(), "Workbook should have at least one sheet");

        ExcelSheet sheet = sheets.get(0);
        List<ExcelChartModel> charts = sheet.getCharts();

        assertNotNull(charts, "Charts collection should not be null");
        assertTrue(!charts.isEmpty(), "Sheet should have at least one chart");

        ExcelChartModel chart = charts.get(0);
        assertNotNull(chart.getType(), "Chart should have a type");

        File targetFile = getTargetFile("generated-bar-chart.xlsx");
        new ExcelTemplate(wk).generateToFile(targetFile, XLang.newEvalScope());

        //File originalDir = getTargetFile("../samples/original-bar-chart");
        //FileHelper.deleteAll(originalDir);

        //ResourceHelper.getZipTool().unzipToDir(resource, new FileResource(originalDir));

        File unzipDir = getTargetFile("generated-bar-chart");
        //FileHelper.deleteAll(unzipDir);

        ResourceHelper.getZipTool().unzipToDir(new FileResource(targetFile), new FileResource(unzipDir));

        ExcelWorkbook savedWk = new ExcelWorkbookParser().parseFromResource(new FileResource(targetFile));
        ExcelSheet savedSheet = savedWk.getSheets().get(0);
        List<ExcelChartModel> savedCharts = savedSheet.getCharts();

        assertNotNull(savedCharts, "Saved workbook charts collection should not be null");
        assertTrue(!savedCharts.isEmpty(), "Saved workbook should have at least one chart");
    }
}
