package io.nop.pdf.extract.struct;


import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;

public class TableCellBlock extends Block implements ICell {

    private int rowIndex;
    private int colIndex;

    /**
     * 行扩展的个数
     */
    private int rowSpan = 1;

    /**
     * 列扩展的个数
     */
    private int colSpan = 1;

    /**
     * 文本内容
     */
    public String content;

    private IRow row;

    private String id;
    private String styleId;

    public TableCellBlock(int row, int col, int rowSpan, int colSpan) {

        this.rowIndex = row;
        this.colIndex = col;

        this.rowSpan = 1;
        this.colSpan = 1;
    }

    @Override
    public ICell cloneInstance() {
        return null;
    }

    @Override
    public IRow getRow() {
        return row;
    }

    @Override
    public void setRow(IRow row) {
        this.row = row;
    }

    @Override
    public void setMergeAcross(int mergeAcross) {
        setColspan(mergeAcross + 1);
    }

    @Override
    public void setMergeDown(int mergeDown) {
        setRowspan(mergeDown + 1);
    }

    @Override
    public void setValue(Object value) {
        this.content = (String) value;
    }

    @Override
    public String getComment() {
        return "";
    }

    @Override
    public void setComment(String comment) {

    }

    @Override
    public boolean frozen() {
        return false;
    }

    @Override
    public void freeze(boolean cascade) {

    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public String getStyleId() {
        return "";
    }

    @Override
    public int getMergeDown() {
        return rowSpan - 1;
    }

    @Override
    public int getMergeAcross() {
        return colSpan - 1;
    }

    @Override
    public String getFormula() {
        return "";
    }

    public int getRowPos() {
        return rowIndex;
    }

    public void setRowPos(int rowIndex) {
        this.rowIndex = rowIndex;
    }


    public int getColPos() {
        return colIndex;
    }


    public void setColPos(int colIndex) {
        this.colIndex = colIndex;
    }


    public int getRowspan() {
        return rowSpan;
    }


    public void setRowspan(int rowSpan) {
        this.rowSpan = rowSpan;
    }


    public int getColspan() {
        return colSpan;
    }

    public void setColspan(int colSpan) {
        this.colSpan = colSpan;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getValue() {
        return content;
    }

    public void setValue(String value) {
        this.content = value;
    }
}
