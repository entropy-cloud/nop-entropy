package io.nop.excel.util;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;

import static io.nop.excel.ExcelErrors.ARG_CELL_REF;
import static io.nop.excel.ExcelErrors.ERR_EXCEL_INVALID_CELL_REF;

@DataBean
public class ExcelCellRef {
    private final String sheetName;
    private final CellRange cellRange;

    public ExcelCellRef(String sheetName, CellRange cellRange) {
        this.sheetName = StringHelper.isEmpty(sheetName) ? null : sheetName;
        this.cellRange = Guard.notNull(cellRange, "cellRange");
    }

    public String toString() {
        if (sheetName == null)
            return cellRange.toString();
        return safeSheetName(sheetName) + '!' + cellRange.toABString();
    }

    /**
     * 必要的时候需要使用引号包裹
     */
    static String safeSheetName(String sheetName) {
        if (sheetName == null || sheetName.isEmpty()) {
            return "''"; // 或抛异常，但通常不会为空
        }

        if (StringHelper.isSafeWordString(sheetName)) {
            return sheetName;
        } else {
            // 转义内部的单引号：Excel 中单引号用两个单引号表示
            String escaped = StringHelper.replace(sheetName, "'", "''");
            return "'" + escaped + "'";
        }
    }

    public ExcelCellRef changeSize(int rowSize, int colSize) {
        return new ExcelCellRef(sheetName, cellRange.changeSize(rowSize, colSize));
    }

    public ExcelCellRef changeSheetName(String sheetName) {
        return new ExcelCellRef(sheetName, cellRange);
    }

    public String getSheetName() {
        return sheetName;
    }

    public CellRange getCellRange() {
        return cellRange;
    }

    /**
     * 解析 SheetName!ABString的格式，支持$C$2这种以及C2这种。可能是A1:C2也可能只是C1，sheetName也是可选的。
     */
    public static ExcelCellRef parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        if (text.equals(CellPosition.NONE_STRING))
            return null;

        String sheetName = null;
        String cellRefStr = text;

        // 检查是否包含工作表名称（格式：SheetName!CellRef）
        int exclamationPos = text.indexOf('!');
        if (exclamationPos >= 0) {
            sheetName = text.substring(0, exclamationPos);
            cellRefStr = text.substring(exclamationPos + 1);

            // 处理工作表名称可能被单引号包围的情况
            if (sheetName.startsWith("'") && sheetName.endsWith("'") && sheetName.length() >= 2) {
                sheetName = StringHelper.replace(sheetName.substring(1, sheetName.length() - 1), "''", "'");
            }

            if (sheetName.isEmpty())
                throw new NopException(ERR_EXCEL_INVALID_CELL_REF)
                        .param(ARG_CELL_REF, cellRefStr);
        }

        // 解析单元格引用部分
        CellRange cellRange = CellRange.fromABString(cellRefStr);
        if (cellRange == null) {
            throw new NopException(ERR_EXCEL_INVALID_CELL_REF)
                    .param(ARG_CELL_REF, cellRefStr);
        }

        return new ExcelCellRef(sheetName, cellRange);
    }
}
