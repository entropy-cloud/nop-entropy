package io.nop.ooxml.xlsx.imp;

import io.nop.api.core.beans.DictBean;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.tree.TreeCell;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelDataValidation;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.xlang.api.XLang;

import java.util.List;
import java.util.UUID;

import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_AUTO_SEQ;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_COL;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_HEADER;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_LABEL;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_SEPARATOR;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_SEQ;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_SEQ_VALUE;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_TITLE;
import static io.nop.ooxml.xlsx.imp.ImportBeanLayout.STYLE_ID_VALUE;

public class ImportModelToExportModel {
    private ExcelWorkbook wk;
    private ExcelSheet dataSheet;
    private String labelStyle;
    private String valueStyle;
    private String titleStyle;
    private String seqStyle;
    private ICache<Object, Object> cache = new MapCache<>("import", false);
    private IEvalScope scope = XLang.newEvalScope();

    public ImportModelToExportModel() {
        wk = new ExcelWorkbookParser().parseFromVirtualPath(XlsxConstants.SIMPLE_DATA_TEMPLATE_PATH);
        dataSheet = wk.getSheet(XlsxConstants.SHEET_DATA);
        wk.clearSheets();

        labelStyle = this.getCellStyleId(0, 0);
        valueStyle = this.getCellStyleId(1, 0);
        titleStyle = this.getCellStyleId(0, 1);
        seqStyle = this.getCellStyleId(0, 2);
    }

    String getCellStyleId(int row, int col) {
        ICellView cell = dataSheet.getTable().getCell(row, col);
        return cell != null ? cell.getStyleId() : null;
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

        ImportBeanLayout layout = new ImportBeanLayout();
        TreeCell rootCell = layout.init(sheetModel);

        assignToTable(sheet.getTable(), rootCell.getChildren());

        addValidation(rootCell, sheetModel, sheet);
        return sheet;
    }

    private void assignToTable(ExcelTable table, List<TreeCell> cells) {
        for (TreeCell cell : cells) {
            if (cell.isVirtual()) {
                if (cell.getChildren() != null)
                    assignToTable(table, cell.getChildren());
                continue;
            }

            ExcelCell ec = new ExcelCell();
            ec.setMergeAcross(cell.getMergeAcross());
            ec.setMergeDown(cell.getMergeDown());
            table.setCell(cell.getRowIndex(), cell.getColIndex(), ec);

            if (STYLE_ID_HEADER.equals(cell.getStyleId())) {
                ec.setStyleId(labelStyle);
                ImportFieldModel field = (ImportFieldModel) cell.getValue();
                ec.setValue(field.getDisplayNameOrName());
            } else if (STYLE_ID_LABEL.equals(cell.getStyleId())) {
                ImportFieldModel field = (ImportFieldModel) cell.getValue();
                ec.setStyleId(labelStyle);
                ec.setValue(field.getDisplayNameOrName());
            } else if (STYLE_ID_VALUE.equals(cell.getStyleId())) {
                ec.setStyleId(valueStyle);
            } else if (STYLE_ID_TITLE.equals(cell.getStyleId())) {
                ec.setStyleId(titleStyle);
                ImportFieldModel field = (ImportFieldModel) cell.getValue();
                ec.setValue(field.getDisplayNameOrName());
            } else if (STYLE_ID_AUTO_SEQ.equals(cell.getStyleId())) {
                ec.setStyleId(labelStyle);
                ec.setValue("序号");
            } else if (STYLE_ID_SEQ.equals(cell.getStyleId())) {
                ec.setStyleId(seqStyle);
                ec.setValue("1");
            } else if (STYLE_ID_SEQ_VALUE.equals(cell.getStyleId())) {
                ec.setStyleId(valueStyle);
                ec.setValue("1");
            } else if (STYLE_ID_SEPARATOR.equals(cell.getStyleId())) {
                ec.setStyleId(valueStyle);
            }
        }
    }

    void addValidation(TreeCell cell, ImportSheetModel sheetModel, ExcelSheet sheet) {
        if (!sheetModel.isList())
            return;

        cell = cell.getChildren().get(0);

        for (TreeCell child : cell.getChildren()) {
            if (!STYLE_ID_COL.equals(child.getStyleId()))
                continue;

            TreeCell fieldCell = child.getChildren().get(1);
            if (!(fieldCell.getValue() instanceof ImportFieldModel))
                continue;

            ImportFieldModel field = (ImportFieldModel) fieldCell.getValue();
            if (field != null && field.getSchema() != null && field.getSchema().getDict() != null) {
                String dictName = field.getSchema().getDict();
                DictBean dict = DictProvider.instance().getDict(null, dictName, cache, scope);

                ExcelDataValidation validation = ExcelDataValidation.buildFromDict(dict, field.isImportDictLabel());
                validation.setId("{" + UUID.randomUUID() + "}");
                String start = CellPosition.toABString(fieldCell.getRowIndex(), fieldCell.getColIndex());
                String end = CellPosition.toABString(CellPosition.MAX_ROWS - 1, fieldCell.getColIndex());
                validation.setSqref(start + ":" + end);
                sheet.addDataValidation(validation);
            }
        }
    }

}