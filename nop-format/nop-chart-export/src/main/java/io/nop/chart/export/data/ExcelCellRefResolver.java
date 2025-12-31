package io.nop.chart.export.data;

import io.nop.api.core.util.Guard;
import io.nop.chart.export.ICellRefResolver;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.ExcelCellRef;

import java.util.ArrayList;
import java.util.List;

public class ExcelCellRefResolver implements ICellRefResolver {
    private final ExcelWorkbook wk;
    private final ExcelSheet sheet;

    public ExcelCellRefResolver(ExcelWorkbook wk, ExcelSheet sheet) {
        this.wk = Guard.notNull(wk, "workbook");
        this.sheet = sheet;
    }

    @Override
    public Object getValue(String cellRef) {
        ExcelCellRef ref = ExcelCellRef.parse(cellRef);
        ExcelSheet sheet = findSheet(ref);
        return sheet.getTable().getCellValue(ref.getCellRange().getFirstRowIndex(), ref.getCellRange().getFirstColIndex());
    }

    ExcelSheet findSheet(ExcelCellRef ref) {
        if (ref.getSheetName() == null)
            return sheet;
        return wk.requireSheet(ref.getSheetName());
    }

    @Override
    public List<Object> getValues(String cellRangeRef) {
        List<Object> values = new ArrayList<>();
        
        // 处理Excel中的多引用组合，比如 (Sheet1!A2,Sheet!B3:B5)
        if (cellRangeRef.startsWith("(") && cellRangeRef.endsWith(")")) {
            // 移除括号
            String multiRef = cellRangeRef.substring(1, cellRangeRef.length() - 1);
            // 分割多个引用
            String[] refs = multiRef.split(",");
            for (String refStr : refs) {
                refStr = refStr.trim();
                if (!refStr.isEmpty()) {
                    ExcelCellRef ref = ExcelCellRef.parse(refStr);
                    addRangeValues(values, ref);
                }
            }
        } else {
            // 单个引用
            ExcelCellRef ref = ExcelCellRef.parse(cellRangeRef);
            addRangeValues(values, ref);
        }
        
        return values;
    }
    
    private void addRangeValues(List<Object> values, ExcelCellRef ref) {
        ExcelSheet sheet = findSheet(ref);
        CellRange range = ref.getCellRange();
        
        // 遍历范围内的所有单元格
        for (int row = range.getFirstRowIndex(); row <= range.getLastRowIndex(); row++) {
            for (int col = range.getFirstColIndex(); col <= range.getLastColIndex(); col++) {
                Object value = sheet.getTable().getCellValue(row, col);
                values.add(value);
            }
        }
    }

    @Override
    public boolean isValidRef(String cellRef) {
        if (cellRef.startsWith("(")) {
            return isMultipleCellRef(cellRef);
        }
        try {
            ExcelCellRef.parse(cellRef);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isMultipleCellRef(String cellRef) {
        if (!cellRef.startsWith("(") || !cellRef.endsWith(")")) {
            return false;
        }
        
        // 移除括号
        String multiRef = cellRef.substring(1, cellRef.length() - 1);
        // 分割多个引用
        String[] refs = multiRef.split(",");
        for (String refStr : refs) {
            refStr = refStr.trim();
            if (!refStr.isEmpty()) {
                try {
                    ExcelCellRef.parse(refStr);
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return true;
    }
}
