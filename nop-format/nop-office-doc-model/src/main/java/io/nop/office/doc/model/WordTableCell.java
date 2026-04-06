package io.nop.office.doc.model;

import io.nop.office.doc.model._gen._WordTableCell;

public class WordTableCell extends _WordTableCell {
    private WordTableCell realCell;
    private int rowOffset;
    private int colOffset;

    public WordTableCellTemplateModel makeModel() {
        WordTableCellTemplateModel model = getModel();
        if (model == null) {
            model = new WordTableCellTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
    }

    @Override
    public boolean isProxyCell() {
        return realCell != null && realCell != this;
    }

    @Override
    public WordTableCell getRealCell() {
        return realCell == null ? this : realCell;
    }

    public void setRealCell(WordTableCell realCell) {
        this.realCell = realCell;
    }

    @Override
    public int getRowOffset() {
        return rowOffset;
    }

    @Override
    public void setRowOffset(int rowOffset) {
        this.rowOffset = rowOffset;
    }

    @Override
    public int getColOffset() {
        return colOffset;
    }

    @Override
    public void setColOffset(int colOffset) {
        this.colOffset = colOffset;
    }

    @Override
    public String getText() {
        Object value = getValue();
        return value == null ? null : value.toString();
    }

    @Override
    public void setValue(Object value) {
        super.setValue(value == null ? null : value.toString());
    }

    @Override
    public String getFormula() {
        return null;
    }

    @Override
    public WordTableCell cloneInstance() {
        WordTableCell cell = new WordTableCell();
        cell.setLocation(getLocation());
        copyExtPropsTo(cell);
        cell.setId(getId());
        cell.setStyleId(getStyleId());
        cell.setComment(getComment());
        cell.setValue(getValue());
        cell.setMergeAcross(getMergeAcross());
        cell.setMergeDown(getMergeDown());
        cell.setModel(getModel());
        return cell;
    }
}
