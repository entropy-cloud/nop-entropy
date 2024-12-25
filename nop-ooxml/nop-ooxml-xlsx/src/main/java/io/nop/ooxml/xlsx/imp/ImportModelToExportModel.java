package io.nop.ooxml.xlsx.imp;

import io.nop.core.model.table.tree.TreeCell;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;

import java.util.List;

public class ImportModelToExportModel {
    private ExcelWorkbook wk;
    private ExcelSheet dataSheet;

    public ImportModelToExportModel() {
        wk = new ExcelWorkbookParser().parseFromVirtualPath(XlsxConstants.SIMPLE_DATA_TEMPLATE_PATH);
        dataSheet = wk.getSheet(XlsxConstants.SHEET_DATA);
        wk.clearSheets();
    }

    public ExcelWorkbook build(ImportModel model) {
        for (ImportSheetModel sheetModel : model.getSheets()) {
            ExcelSheet sheet = buildSheet(sheetModel);
            wk.addSheet(sheet);
        }
        return wk;
    }

    ExcelSheet buildSheet(ImportSheetModel sheetModel) {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName(sheetModel.getName());
        sheet.setLocation(sheetModel.getLocation());
        return sheet;
    }

    private void layout(List<TreeCell> cells, List<ImportFieldModel> fields, boolean list) {
        if (list) {
            boolean complex = hasSubFields(fields);

        } else {

        }
    }

    private boolean hasSubFields(List<ImportFieldModel> fields) {
        for (ImportFieldModel field : fields) {
            if (field.hasFields())
                return true;
        }
        return false;
    }
}