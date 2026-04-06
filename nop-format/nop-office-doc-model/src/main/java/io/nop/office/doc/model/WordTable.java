package io.nop.office.doc.model;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.impl.BaseColumnConfig;
import io.nop.office.doc.model._gen._WordTable;

import java.util.ArrayList;

public class WordTable extends _WordTable implements OfficeBlock {
    public WordTable() {
        setRows(new ArrayList<>());
        setCols(new ArrayList<>());
    }

    public WordTableTemplateModel makeModel() {
        WordTableTemplateModel model = getModel();
        if (model == null) {
            model = new WordTableTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }

    @Override
    protected WordTableRow newRow() {
        return new WordTableRow();
    }

    @Override
    public WordTableCell newProxyCell(ICell cell, int rowOffset, int colOffset) {
        WordTableCell proxy = new WordTableCell();
        proxy.setRealCell((WordTableCell) cell);
        proxy.setRowOffset(rowOffset);
        proxy.setColOffset(colOffset);
        return proxy;
    }

    public WordTableCell newCell() {
        return new WordTableCell();
    }

    public WordTable cloneInstance() {
        WordTable table = new WordTable();
        table.setLocation(getLocation());
        copyExtPropsTo(table);
        table.setId(getId());
        table.setStyleId(getStyleId());
        table.setHeaderCount(getHeaderCount());
        table.setSideCount(getSideCount());
        table.setFooterCount(getFooterCount());
        table.setModel(getModel());
        if (getCols() != null) {
            for (WordTableColumnConfig col : getCols()) {
                table.getCols().add(col == null ? null : col.cloneInstance());
            }
        }
        for (int i = 0, n = getRowCount(); i < n; i++) {
            WordTableRow row = getRow(i);
            if (row == null) {
                continue;
            }
            WordTableRow clonedRow = table.makeRow(i);
            clonedRow.setLocation(row.getLocation());
            clonedRow.setId(row.getId());
            clonedRow.setStyleId(row.getStyleId());
            clonedRow.setHeight(row.getHeight());
            clonedRow.setHidden(row.isHidden());
            row.copyExtPropsTo(clonedRow);
        }
        this.forEachRealCell((cell, rowIndex, colIndex) -> {
            table.setCell(rowIndex, colIndex, ((WordTableCell) cell).cloneInstance());
            return ProcessResult.CONTINUE;
        });
        return table;
    }

    public void addColumn(BaseColumnConfig config) {
        checkAllowChange();
        WordTableColumnConfig col = new WordTableColumnConfig();
        col.setWidth(config.getWidth());
        col.setHidden(config.isHidden());
        col.setStyleId(config.getStyleId());
        getCols().add(col);
    }
}
