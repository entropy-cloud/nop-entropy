package io.nop.tablesaw.xlsx;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * XlsxReader 与上游 tablesaw 语义对齐的回归：
 * 1. 未写过的单元格（稠密模型中的null）在POI行迭代器中不可见，findRowArea必须跳过而不是break，
 *    否则中间有空列的sheet整列丢失（修复前只剩空洞前的第一列）
 * 2. 列类型合并必须在收集完成后统一加宽归一化，逐单元格增量合并依赖出现顺序，
 *    [19.99, 20]会残留{DOUBLE,INT}并整列降级为STRING
 */
public class TestXlsxReaderRegression extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static ExcelWorkbook workbook(Object[][] rows) {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("s1");
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < rows[i].length; j++) {
                Object value = rows[i][j];
                if (value == null)
                    continue; // 不写该单元格
                ExcelCell cell = new ExcelCell();
                cell.setValue(value);
                sheet.getTable().setCell(i, j, cell);
            }
        }
        wk.addSheet(sheet);
        return wk;
    }

    static Table read(ExcelWorkbook wk) throws IOException {
        File file = new File("_tmp/test-xlsx-reader.xlsx");
        if (file.getParentFile() != null)
            file.getParentFile().mkdirs();
        new ExcelTemplate(wk).generateToFile(file, XLang.newEvalScope());
        return new XlsxReader().read(XlsxReadOptions.builder(file).build());
    }

    @Test
    public void testInteriorNeverWrittenColumnKept() throws IOException {
        // B列两行都未写
        Table table = read(workbook(new Object[][]{
                {"Name", null, "Age"},
                {"x", null, "y"}
        }));

        // 修复前findRowArea在B列null处break，行区域只剩第0列，Age列整列丢失
        // 注：B列空洞使表头检测失败（可见单元数2!=列数3），与上游一致按无表头处理
        assertEquals(2, table.columnCount());
        assertEquals("Name", table.stringColumn("col0").get(0));
        assertEquals("x", table.stringColumn("col0").get(1));
        assertEquals("Age", table.stringColumn("col2").get(0));
        assertEquals("y", table.stringColumn("col2").get(1));
    }

    @Test
    public void testColumnWithMixedDoubleIntStaysDouble() throws IOException {
        Table table = read(workbook(new Object[][]{
                {"Price"},
                {19.99},
                {20}
        }));

        // 修复前依赖顺序的增量合并得到{DOUBLE,INT}，整列降级为STRING
        assertEquals(ColumnType.DOUBLE, table.column("Price").type());
        assertEquals(19.99, table.doubleColumn("Price").getDouble(0), 1e-9);
        assertEquals(20.0, table.doubleColumn("Price").getDouble(1), 1e-9);
    }

    @Test
    public void testLongAndIntWidenToLong() throws IOException {
        Table table = read(workbook(new Object[][]{
                {"V"},
                {3000000000L},
                {1}
        }));

        assertEquals(ColumnType.LONG, table.column("V").type());
    }
}
