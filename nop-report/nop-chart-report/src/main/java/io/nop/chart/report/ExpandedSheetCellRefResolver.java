package io.nop.chart.report;

import io.nop.excel.model.IExcelSheet;
import io.nop.excel.resolver.ICellRefResolver;
import io.nop.excel.util.ExcelCellRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Cell reference resolver over an expanded report sheet ({@link IExcelSheet} table).
 *
 * <p>Values are read through the table's null-safe {@code getCellValue(row,col)}
 * (inherited from ITableView). Sheet-qualified references resolve locally when the
 * qualifier names this sheet -- chart dataRefs from a template carry their own
 * sheet's name, and the expanded sheet keeps that name. References to other sheets
 * are rejected explicitly because ExpandedSheet carries no workbook back-reference
 * (plan 2365 D45, refined during execution: the fixture itself uses
 * {@code Sheet1!$A$3}-style self-qualified refs).</p>
 */
public class ExpandedSheetCellRefResolver implements ICellRefResolver {

    private final IExcelSheet sheet;

    public ExpandedSheetCellRefResolver(IExcelSheet sheet) {
        this.sheet = sheet;
    }

    @Override
    public Object getValue(String cellRef) {
        ExcelCellRef ref = parse(cellRef);
        return sheet.getTable().getCellValue(ref.getCellRange().getFirstRowIndex(),
                ref.getCellRange().getFirstColIndex());
    }

    @Override
    public List<Object> getValues(String cellRangeRef) {
        List<Object> values = new ArrayList<>();
        if (cellRangeRef.startsWith("(") && cellRangeRef.endsWith(")")) {
            String multiRef = cellRangeRef.substring(1, cellRangeRef.length() - 1);
            String[] refs = multiRef.split(",");
            for (String refStr : refs) {
                refStr = refStr.trim();
                if (!refStr.isEmpty())
                    addRangeValues(values, parse(refStr));
            }
        } else {
            addRangeValues(values, parse(cellRangeRef));
        }
        return values;
    }

    private ExcelCellRef parse(String cellRef) {
        ExcelCellRef ref = ExcelCellRef.parse(cellRef);
        if (ref.getSheetName() != null && !isLocalSheet(ref.getSheetName()))
            throw new UnsupportedOperationException(
                    "cross-sheet chart data reference is not supported on expanded sheets: " + cellRef
                            + " (sheet is '" + sheet.getName() + "')");
        return ref;
    }

    /**
     * A sheet-qualified reference resolves locally when the qualifier names this
     * sheet (the expanded name defaults to the template sheet name).
     */
    private boolean isLocalSheet(String sheetName) {
        return sheetName.equals(sheet.getName());
    }

    private void addRangeValues(List<Object> values, ExcelCellRef ref) {
        var range = ref.getCellRange();
        for (int row = range.getFirstRowIndex(); row <= range.getLastRowIndex(); row++) {
            for (int col = range.getFirstColIndex(); col <= range.getLastColIndex(); col++) {
                values.add(sheet.getTable().getCellValue(row, col));
            }
        }
    }

    @Override
    public boolean isValidRef(String cellRef) {
        try {
            if (cellRef.startsWith("(")) {
                String multiRef = cellRef.substring(1, cellRef.length() - 1);
                for (String refStr : multiRef.split(",")) {
                    refStr = refStr.trim();
                    if (!refStr.isEmpty())
                        ExcelCellRef.parse(refStr);
                }
                return true;
            }
            ExcelCellRef.parse(cellRef);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
