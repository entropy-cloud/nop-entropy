package io.nop.ooxml.xlsx.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordOutputProvider;
import io.nop.dataset.record.IRecordOutput;
import io.nop.excel.format.ExcelDateHelper;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.WorkbookPart;
import io.nop.ooxml.xlsx.model.XSSFSheetRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.nop.ooxml.xlsx.XlsxErrors.ARG_REL_ID;
import static io.nop.ooxml.xlsx.XlsxErrors.ARG_TYPE;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_NULL_REL_PART;

public class XlsxToRecordOutput extends AbstractXlsxParser {
    private final IResourceRecordOutputProvider<List<Object>> recordIO;
    private final Function<String, IResource> outputResourceLocator;
    private final String encoding;

    public XlsxToRecordOutput(IResourceRecordOutputProvider<List<Object>> recordIO, String encoding,
                              Function<String, IResource> outputResourceLocator) {
        this.recordIO = Guard.notNull(recordIO, "recordIO");
        this.encoding = encoding;
        this.outputResourceLocator = Guard.notNull(outputResourceLocator, "resourceLocator");
    }

    @Override
    protected ExcelSheet parseSheet(ExcelWorkbook workbook, XSSFSheetRef sheetRef, WorkbookPart workbookFile) {
        IResource outputFile = outputResourceLocator.apply(sheetRef.getName());
        if (outputFile == null)
            return null;

        IOfficePackagePart sheetPart = pkg.getRelPart(workbookFile, sheetRef.getRelId());
        if (sheetPart == null)
            throw new NopException(ERR_XLSX_NULL_REL_PART).param(ARG_TYPE, "sheet").param(ARG_REL_ID, sheetRef.getRelId());


        IRecordOutput<List<Object>> output = recordIO.openOutput(outputFile, encoding);
        try {
            OutputRowHandler contentsHandler = new OutputRowHandler(workbook, output);

            SheetNodeHandler handler = new SheetNodeHandler(sharedStringsTable, contentsHandler);
            sheetPart.processXml(handler, null);
        } finally {
            IoHelper.safeCloseObject(output);
        }
        return null;
    }

    static class OutputRowHandler implements SheetContentsHandler {
        private final ExcelWorkbook workbook;
        private final IRecordOutput<List<Object>> output;
        private List<Object> row;

        public OutputRowHandler(ExcelWorkbook workbook, IRecordOutput<List<Object>> output) {
            this.workbook = workbook;
            this.output = output;
        }

        @Override
        public void startSheet(String sheetName) {

        }

        @Override
        public void cols(List<ExcelColumnConfig> cols) {

        }

        @Override
        public void pageMargins(ExcelPageMargins pageMargins) {

        }

        @Override
        public void sheetFormat(Double defaultRowHeight) {

        }

        @Override
        public void startRow(int rowNum, Double height, boolean hidden) {
            this.row = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            output.write(row);
        }

        @Override
        public void cell(CellPosition cellRef, Object value, String formulaStr, int styleId) {
            if (styleId >= 0) {
                if (value instanceof Double) {
                    ExcelStyle style = workbook.getStyle(String.valueOf(styleId));
                    if (style != null && style.isDateFormat()) {
                        value = ExcelDateHelper.excelDateToLocalDateTime((Double) value);
                    }
                }
            }

            CollectionHelper.set(row, cellRef.getColIndex(), value);
        }

        @Override
        public void mergeCell(CellRange range) {

        }

        @Override
        public void drawing(String id) {

        }
    }
}
