package io.nop.ofbiz.migration.transform;

import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1-4.5-D1 (plan 2403): the catalog sheet's sequence column must match the row
 * position. Before the fix `makeRow(index++)` was followed by `setValue(index)`, so
 * the first data row (row 1) received 2 and every row was off by one.
 */
class TestEntityDefCatalogAddCatalog {

    private ExcelWorkbook newCatalogWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("目录");
        workbook.addSheet(sheet);
        ExcelTable table = sheet.getTable();
        // addCatalog reads the header styles from row index 1, columns 0..3
        for (int col = 0; col < 4; col++) {
            ExcelCell cell = (ExcelCell) table.makeCell(1, col);
            cell.setStyleId("style-" + col);
        }
        return workbook;
    }

    private OrmModel twoEntities() {
        OrmModel model = new OrmModel();
        // OrmModel keys entities by name — null names would collapse into one row
        OrmEntityModel a = new OrmEntityModel();
        a.setName("A");
        a.setTableName("t_a");
        a.setDisplayName("A");
        a.setComment("a");
        model.addEntity(a);
        OrmEntityModel b = new OrmEntityModel();
        b.setName("B");
        b.setTableName("t_b");
        b.setDisplayName("B");
        b.setComment("b");
        model.addEntity(b);
        return model;
    }

    @Test
    void catalogSequenceMatchesRowPosition() {
        ExcelWorkbook workbook = newCatalogWorkbook();
        ExcelTable table = workbook.getSheet("目录").getTable();

        EntityDefDirTransformer.addCatalog(workbook, twoEntities());

        assertNotNull(table.getCell(1, 0), "first entity row must be written");
        assertEquals(1, ((Number) table.getCell(1, 0).getValue()).intValue(),
                "first catalog row must be sequence 1 (pre-fix was 2)");
        assertNotNull(table.getCell(2, 0), "second entity row must be written");
        assertEquals(2, ((Number) table.getCell(2, 0).getValue()).intValue(),
                "second catalog row must be sequence 2 (pre-fix was 3)");
    }
}
