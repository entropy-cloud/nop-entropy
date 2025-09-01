/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.coordinate;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * 单元格坐标表达式
 * cellLayerCoordinate ::= CellName ('[' cellCoordinates ';' cellCoordinates ']') ?
 * cellCoordinates ::= cellCoordinate (',' cellCoordinate) *
 * cellCoordinate ::= CellName (':' '!'? Position) ?
 */
@DataBean
public class CellLayerCoordinate {
    private String cellName;
    private int position;
    private List<CellCoordinate> rowCoordinates;
    private List<CellCoordinate> colCoordinates;

    public boolean hasParent() {
        if (rowCoordinates != null && !rowCoordinates.isEmpty())
            return true;
        if (colCoordinates != null && !colCoordinates.isEmpty())
            return true;
        return false;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(cellName);
        if (rowCoordinates != null || colCoordinates != null) {
            sb.append('[');
            if (rowCoordinates != null) {
                appendCoordinates(sb, rowCoordinates);
            }

            if (colCoordinates != null) {
                sb.append(';');
                appendCoordinates(sb, colCoordinates);
            }
            sb.append(']');
        }
        return sb.toString();
    }

    void appendCoordinates(StringBuilder sb, List<CellCoordinate> coordinates) {
        for (int i = 0, n = coordinates.size(); i < n; i++) {
            if (i != 0)
                sb.append(',');
            coordinates.get(i).appendTo(sb);
        }
    }

    public String getCellName() {
        return cellName;
    }

    public void setCellName(String cellName) {
        this.cellName = cellName;
    }

    public List<CellCoordinate> getRowCoordinates() {
        return rowCoordinates;
    }

    public void setRowCoordinates(List<CellCoordinate> rowCoordinates) {
        this.rowCoordinates = rowCoordinates;
    }

    public List<CellCoordinate> getColCoordinates() {
        return colCoordinates;
    }

    public void setColCoordinates(List<CellCoordinate> colCoordinates) {
        this.colCoordinates = colCoordinates;
    }
}