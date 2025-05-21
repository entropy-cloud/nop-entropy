package io.nop.pdf.extract.struct;

import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.impl.BaseTable;

import java.util.ArrayList;
import java.util.List;


public class TableBlock extends Block {

    /**
     * 行数
     */
    // private int rowCount;

    /**
     * 列数
     */
    // private int colCount;

    /**
     * 所有列边线的坐标，包括左右两边，个数比列数多1
     */
    private List<Double> xpoints;

    /**
     * 所有行边线的坐标，包括上下两边，个数比行数多1
     */
    private List<Double> ypoints;

    /**
     * 所有的单元格
     */
    //private Map<String, TableCellBlock> cells;
    BaseTable table = new BaseTable();

    int minCellBlockIndex = -1;
    int maxCellBlockIndex = -1;

    /**
     * 是否为合并后的表格
     */
    private boolean merged = false;

    /**
     * 表格结束部分所在的页码
     */
    private int endingPageNo = -1;

    public void simplify() {
        //保留xpoints用于表格合并
        //if(xpoints != null)
        //	xpoints.clear();

        if (ypoints != null)
            ypoints.clear();
    }

    public int getMinCellBlockIndex() {
        return minCellBlockIndex;
    }

    public void setMinCellBlockIndex(int minCellBlockIndex) {
        this.minCellBlockIndex = minCellBlockIndex;
    }

    public int getMaxCellBlockIndex() {
        return maxCellBlockIndex;
    }

    public void setMaxCellBlockIndex(int maxCellBlockIndex) {
        this.maxCellBlockIndex = maxCellBlockIndex;
    }

    public int getRowCount() {
        return table.getRowCount();
    }

    public int getColCount() {
        return table.getColCount();
    }

    public void setColCount(int colCount) {

    }

    public List<Double> getXpoints() {
        return xpoints;
    }

    public void setXpoints(List<Double> xpoints) {
        this.xpoints = xpoints;
    }

    public List<Double> getYpoints() {
        return ypoints;
    }

    public void setYpoints(List<Double> ypoints) {
        this.ypoints = ypoints;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public int getEndingPageNo() {
        return endingPageNo;
    }

    public void setEndingPageNo(int endingPageNo) {
        this.endingPageNo = endingPageNo;
    }

    public BaseTable getTable() {
        return table;
    }

    @SuppressWarnings("unchecked")
    public List<TableCellBlock> getRowCells(int rowIdx) {
        return (List<TableCellBlock>) table.getRow(rowIdx).getCells();
    }

    public TableCellBlock getCell(int rowIdx, int colIdx) {
        return (TableCellBlock) table.getCell(rowIdx, colIdx);
    }

    public void addCell(int rowIdx, int colIdx, TableCellBlock cell) {
        table.setCell(rowIdx, colIdx, cell);
    }

    public boolean containsCellBlock(int index) {
        return minCellBlockIndex <= index && index <= maxCellBlockIndex;
    }

    public int resetCellBlockIndex(int index) {

        this.minCellBlockIndex = index;

        for (int i = 0, rowCount = getRowCount(); i < rowCount; i++) {

            for (int j = 0, colCount = getColCount(); j < colCount; j++) {

                TableCellBlock cell = getCell(i, j);
                if (cell != null) {
                    cell.setPageBlockIndex(index);
                    cell.setPageNo(getPageNo());
                    index++;
                }
            }
        }
        this.maxCellBlockIndex = index - 1;
        return index;
    }

    public TableCellBlock getCellBlockByIndex(int index) {
        for (int i = 0, n = getRowCount(); i < n; i++) {
            for (ICellView cell : table.getRowCells(i)) {
                TableCellBlock block = (TableCellBlock) cell;
                if (block.getPageBlockIndex() == index)
                    return block;
            }
        }
        return null;
    }

    public void rebuildXpoints() {

        List<Double> pts = new ArrayList<Double>();
        for (int i = 0; i < this.getColCount(); i++) {

            pts.add(Double.MIN_VALUE);
            for (int j = 0; j < this.getRowCount(); j++) {

                TableCellBlock cell = this.getCell(j, i);
                if (cell != null) {

                    double minx = cell.getViewBounding().getMinX();
                    pts.set(i, minx);
                    break;
                }
            }
        }

        // 最后一列
        pts.add(this.getViewBounding().getMaxX());

        this.setXpoints(pts);
    }
}
