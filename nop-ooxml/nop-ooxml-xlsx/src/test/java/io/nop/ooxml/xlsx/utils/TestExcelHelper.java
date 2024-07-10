package io.nop.ooxml.xlsx.utils;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestExcelHelper extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testXlsxToCsv() {
        forceStackTrace();

        IResource xlsx = getTargetResource("data.xlsx");
        File targetFile = getTargetFile("result.csv");
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("Sheet1");
        workbook.addSheet(sheet);

        System.gc();
        long memory = Runtime.getRuntime().freeMemory();
        ExcelTable table = sheet.getTable();
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 10; j++) {
                ExcelCell cell = new ExcelCell();
                cell.setValue("v" + i + "_" + j);
                table.setCell(i, j, cell);
            }
        }

        ExcelHelper.saveExcel(xlsx, workbook);
        System.gc();
        long endMemory = Runtime.getRuntime().freeMemory();
        // 100万记录输出 usedMemory=6066
        System.out.println("usedMemory=" + (memory - endMemory) / 1024);

        long beginTime = CoreMetrics.currentTimeMillis();
        ExcelHelper.xlsxToCsv(xlsx, targetFile, true, null);
        long endTime = CoreMetrics.currentTimeMillis();
        // 1M => 13095ms
        System.out.println("xlsxToCsv:" + (endTime - beginTime) + "ms");

        beginTime = CoreMetrics.currentTimeMillis();
        ExcelHelper.readXlsx(xlsx, null);
        endTime = CoreMetrics.currentTimeMillis();
        // 1M => 16312ms
        System.out.println("readXlsx:" + (endTime - beginTime) + "ms");
    }
}
