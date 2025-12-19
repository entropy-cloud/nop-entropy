package io.nop.ooxml.xlsx.utils;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.IComponentModel;
import io.nop.commons.type.StdDataType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelDataValidation;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.ExcelDataValidationHelper;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.ooxml.xlsx.util.ExcelSheetData;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ExcelHelper.readSheet(xlsx, null, 0);
        endTime = CoreMetrics.currentTimeMillis();
        // 1M => 16312ms
        System.out.println("readXlsx:" + (endTime - beginTime) + "ms");
    }

    @Test
    public void readAllSheets() {
        IResource xlsx = attachmentResource("test-data.xlsx");
        List<ExcelSheetData> sheets = ExcelHelper.readAllSheets(xlsx);
        System.out.println(JsonTool.serialize(sheets, true));
        assertEquals(2, sheets.size());
        assertEquals("Sheet2", sheets.get(1).getName());
        assertEquals("[{c=5, d=6}, {c=7, d=8}]", sheets.get(1).getData().toString());
    }

    @Test
    public void testApi() {
        IResource resource = attachmentResource("test.api.xlsx");
        String impPath = "/test/test-api.imp.xml";
        IComponentModel model = (IComponentModel) ExcelHelper.loadXlsxObject(impPath, resource);
        String xdefPath = ResourceComponentManager.instance().getXDefPathByModelPath(resource.getPath());
        XNode node = DslModelHelper.dslModelToXNode(xdefPath, model);
        node.dump();
        assertTrue(node.xml().contains("packageName=\"abc\""));
    }

    @Test
    public void testGenXlsx() {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("Sheet1");
        wk.addSheet(sheet);

        char c = 'A';
        for (StdDataType dataType : StdDataType.values()) {
            if(dataType.ordinal() > 15)
                break;

            SchemaImpl schema = new SchemaImpl();
            schema.setType(ReflectionManager.instance().buildRawType(dataType.getJavaClass()));
            if(dataType == StdDataType.STRING)
                schema.setMaxLength(20);

            String sqref = c +"2:" + c + "5";
            c++;
            ExcelDataValidation validation = ExcelDataValidationHelper.newDataValidation(schema, true, "Field_" + dataType, sqref);
            sheet.addDataValidation(validation);
        }

        ExcelHelper.saveExcel(getTargetResource("validation-result.xlsx"), wk);
    }
}
