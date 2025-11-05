package io.nop.pdf.tabula;

import io.nop.core.model.table.impl.BaseCell;
import io.nop.core.model.table.impl.BaseTable;

import java.util.List;

public class TabulaTableHelper {
    public static BaseTable toBaseTable(Table table) {
        BaseTable ret = new BaseTable();
        int rowCount = table.getRowCount();
        List<List<RectangularTextContainer>> rows = table.getRows();

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<RectangularTextContainer> row = rows.get(rowIndex);
            int colIndex = 0;
            for (RectangularTextContainer cell : row) {
                BaseCell retCell = new BaseCell();
                retCell.setValue(cell.getText());
                ret.setCell(rowIndex, colIndex, retCell);
                colIndex++;
            }
        }
        return ret;
    }
}
