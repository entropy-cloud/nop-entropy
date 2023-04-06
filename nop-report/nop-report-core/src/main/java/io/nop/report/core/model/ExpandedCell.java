/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.Underscore;
import io.nop.core.model.table.ICellView;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.dataset.KeyedReportDataSet;
import io.nop.report.core.dataset.ReportDataSet;
import io.nop.report.core.engine.IXptRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 报表展开过程中需要频繁修改行和列，所以采用单向列表形式来维护
 */
public class ExpandedCell implements ICellView {
    private XptCellModel model;

    private String id;
    private String styleId;
    private Object value;
    private String comment;
    private int mergeDown;
    private int mergeAcross;

    private Object formattedValue;
    private String linkUrl;

    // 对于合并单元格，realCell设置为左上角的单元格
    // private int rowOffset;
    //  private int colOffset;
    private ExpandedCell realCell;

    private ExpandedCell right;
    private ExpandedCell down;

    private ExpandedCell rowParent;
    private ExpandedCell colParent;

    // 递归包含所有子单元格
    private Map<String, List<ExpandedCell>> rowDescendants = null;
    private Map<String, List<ExpandedCell>> colDescendants = null;

    private Object expandValue;
    private int expandIndex = -1; // 在展开列表中的下标

    private ExpandedRow row;
    private ExpandedCol col;

    private boolean removed;

    /**
     * valueExpr已经执行完毕，value值可用
     */
    private boolean evaluated;

    /**
     * 缓存与单元格有关的动态计算的值
     */
    private Map<String, Object> computedValues;

    public String toString() {
        return "ExpandedCell[name=" + getName() + ",expandIndex=" + getExpandValue() + ",text=" + getText() + "]";
    }

    public Object getComputed(String key, Function<ExpandedCell, Object> fn) {
        if (computedValues == null) {
            computedValues = new HashMap<>();
        }
        return computedValues.computeIfAbsent(key, k -> fn.apply(this));
    }


    /**
     * 标记colSpan和rowSpan范围内的所有单元格的realCell为当前单元格。
     */
    public void markProxy() {
        Guard.checkState(!isProxyCell());

        ExpandedCell cell = this;
        for (int i = 0; i <= mergeDown; i++) {
            ExpandedCell colCell = cell;
            for (int j = 0; j <= mergeAcross; j++) {
                colCell.setRealCell(this);
                colCell = colCell.getRight();
            }
            cell = cell.getDown();
        }
    }

    public Number getNumberValue() {
        Object value = getValue();
        if (value instanceof Number)
            return (Number) value;
        return null;
    }

    public ReportDataSet getDs() {
        if (expandValue instanceof ReportDataSet)
            return (ReportDataSet) expandValue;
        return null;
    }

    public String getDsName() {
        ReportDataSet ds = getDs();
        if (ds != null)
            return ds.getDsName();
        return model.getDs();
    }

    public int getRowIndex() {
        return row.getRowIndex();
    }

    public int getColIndex() {
        return col.getColIndex();
    }

    public void markEvaluated() {
        setEvaluated(true);
        setExpandValue(null);
        setExpandIndex(0);
        if (!isStaticCell()) {
            setValue(null);
        }
    }

    public boolean isStaticCell() {
        XptCellModel model = getModel();
        if (model == null)
            return true;
        if (model.getExpandType() != null || model.getExpandExpr() != null)
            return false;

        if (model.getValueExpr() != null)
            return false;

        if (model.getField() != null)
            return false;
        return true;
    }

    public Object getFormattedValue() {
        if (formattedValue == null)
            return value;
        return formattedValue;
    }

    public void setFormattedValue(Object formattedValue) {
        this.formattedValue = formattedValue;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public boolean isEvaluated() {
        return evaluated;
    }

    public void setEvaluated(boolean evaluated) {
        this.evaluated = evaluated;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setModel(XptCellModel model) {
        this.model = model;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public ICellView cloneInstance() {
        return this;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        // 被删除的单元格，它的值被强制设置为null
        if (removed) {
            setEvaluated(true);
            setValue(null);
        }
        this.removed = removed;
    }

    public String getName() {
        return model == null ? null : model.getName();
    }

    public boolean isExpanded() {
        return expandIndex >= 0 || model == null || model.getExpandType() == null;
    }

    public XptExpandType getExpandType() {
        return model == null ? null : model.getExpandType();
    }

    @Override
    public Object getExportValue() {
        if (model.isExportFormattedValue())
            return getFormattedValue();
        return getValue();
    }

    public int getRowParentExpandIndex() {
        if (rowParent == null)
            return -1;
        return rowParent.getExpandIndex();
    }

    public int getColParentExpandIndex() {
        if (colParent == null)
            return -1;
        return colParent.getExpandIndex();
    }

    public boolean isProxyCell() {
        return realCell != null && realCell != this;
    }

    public ExpandedTable getTable() {
        return getRow().getTable();
    }

    public ExpandedCell getRowRoot() {
        if (rowParent == null)
            return this;
        return rowParent.getRowRoot();
    }

    public ExpandedCell getColRoot() {
        if (colParent == null)
            return this;
        return colParent.getColRoot();
    }

    public XptCellModel getModel() {
        return model;
    }

    public boolean isExpandable() {
        XptCellModel model = getModel();
        if (model == null)
            return false;
        return model.getExpandType() != null;
    }

    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public ExpandedCell getRight() {
        return right;
    }

    public void setRight(ExpandedCell right) {
        this.right = right;
    }

    public int getMergeDown() {
        return mergeDown;
    }

    public void setMergeDown(int mergeDown) {
        this.mergeDown = mergeDown;
    }

    public int getMergeAcross() {
        return mergeAcross;
    }

    public void setMergeAcross(int mergeAcross) {
        this.mergeAcross = mergeAcross;
    }

    @JsonIgnore
    public ExpandedCell getRealCell() {
        if (realCell == null)
            return this;
        return realCell;
    }

    public void setRealCell(ExpandedCell realCell) {
        this.realCell = realCell;
    }

    @JsonIgnore
    public ExpandedCell getDown() {
        return down;
    }

    public void setDown(ExpandedCell down) {
        this.down = down;
    }

    @JsonIgnore
    public ExpandedCell getRowParent() {
        return rowParent;
    }

    public void setRowParent(ExpandedCell rowParent) {
        this.rowParent = rowParent;
    }

    public ExpandedCell getRowClosest(String cellName) {
        if (cellName.equals(this.getName()))
            return this;

        if (this.rowParent == null)
            return null;
        if (cellName.equals(this.rowParent.getName()))
            return this.rowParent;
        return this.rowParent.getRowClosest(cellName);
    }

    public ExpandedCell getColClosest(String cellName) {
        if (cellName.equals(this.getName()))
            return this;

        if (this.colParent == null)
            return null;
        if (cellName.equals(this.colParent.getName()))
            return colParent;
        return this.colParent.getColClosest(cellName);
    }

    public List<ExpandedCell> getRowChildren() {
        if (rowDescendants == null || rowDescendants.isEmpty())
            return Collections.emptyList();

        List<ExpandedCell> children = new ArrayList<>();
        for (String cellName : model.getRowChildCells().keySet()) {
            List<ExpandedCell> list = rowDescendants.get(cellName);
            if (list != null) {
                for (ExpandedCell cell : list) {
                    if (cell.getRowParent() == this) {
                        children.add(cell);
                    }
                }
            }
        }
        return children;
    }

    public List<ExpandedCell> getColChildren() {
        if (colDescendants == null || colDescendants.isEmpty())
            return Collections.emptyList();

        List<ExpandedCell> children = new ArrayList<>();
        for (String cellName : model.getColChildCells().keySet()) {
            List<ExpandedCell> list = colDescendants.get(cellName);
            if (list != null) {
                for (ExpandedCell cell : list) {
                    if (cell.getColParent() == this) {
                        children.add(cell);
                    }
                }
            }
        }
        return children;
    }

    public ExpandedCell getColParent() {
        return colParent;
    }

    public void setColParent(ExpandedCell colParent) {
        this.colParent = colParent;
    }

    public Object getExpandValue() {
        return expandValue;
    }

    public void setExpandValue(Object expandValue) {
        this.expandValue = expandValue;
    }

    public Object getExpandKey() {
        if (expandValue instanceof KeyedReportDataSet)
            return ((KeyedReportDataSet) expandValue).getKey();
        return null;
    }

    @EvalMethod
    public Object getExpandField(IEvalScope scope, String name) {
        if (expandValue != null) {
            if (expandValue instanceof ReportDataSet) {
                return ((ReportDataSet) expandValue).field(scope, name);
            }
            return Underscore.getFieldValue(expandValue, name);
        }
        return Underscore.getFieldValue(value, name);
    }

    public int getExpandIndex() {
        return expandIndex;
    }

    public void setExpandIndex(int expandIndex) {
        this.expandIndex = expandIndex;
    }

    public ExpandedRow getRow() {
        return row;
    }

    public void setRow(ExpandedRow row) {
        this.row = row;
    }

    public ExpandedCol getCol() {
        return col;
    }

    public void setCol(ExpandedCol col) {
        this.col = col;
    }

    public Map<String, List<ExpandedCell>> getRowDescendants() {
        return rowDescendants;
    }

    public ExpandedCellSet getChildSet(String cellName, IXptRuntime xptRt) {
        if (rowDescendants != null) {
            List<ExpandedCell> cells = rowDescendants.get(cellName);
            if (cells != null && !cells.isEmpty())
                return new ExpandedCellSet(null, cellName, cells).evaluateAll(xptRt);
        }
        if (colDescendants != null) {
            List<ExpandedCell> cells = colDescendants.get(cellName);
            if (cells != null && !cells.isEmpty())
                return new ExpandedCellSet(null, cellName, cells).evaluateAll(xptRt);
        }
        return null;
    }

    public boolean hasRowDescendant() {
        return rowDescendants != null && !rowDescendants.isEmpty();
    }

    public boolean hasColDescendant() {
        return colDescendants != null && !colDescendants.isEmpty();
    }

    public void setRowDescendants(Map<String, List<ExpandedCell>> rowDescendants) {
        this.rowDescendants = rowDescendants;
    }

    public void addRowChild(ExpandedCell cell) {
        if (rowDescendants == null)
            rowDescendants = new HashMap<>();

        addToList(rowDescendants, cell);

        ExpandedCell p = rowParent;
        while (p != null) {
            if (p.rowDescendants == null)
                p.rowDescendants = new HashMap<>();
            addToList(p.rowDescendants, cell);
            p = p.getRowParent();
        }
    }

    public void addColChild(ExpandedCell cell) {
        if (colDescendants == null)
            colDescendants = new HashMap<>();

        addToList(colDescendants, cell);

        ExpandedCell p = colParent;
        while (p != null) {
            if (p.colDescendants == null)
                p.colDescendants = new HashMap<>();
            addToList(p.colDescendants, cell);
            p = p.getColParent();
        }
    }

    void addToList(Map<String, List<ExpandedCell>> map, ExpandedCell cell) {
        List<ExpandedCell> list = map.get(cell.getName());
        if (list == null) {
            list = new ArrayList<>();
            map.put(cell.getName(), list);
        }
        list.add(cell);
    }

    void removeFromList(Map<String, List<ExpandedCell>> map, ExpandedCell cell) {
        List<ExpandedCell> list = map.get(cell.getName());
        if (list != null)
            list.remove(cell);
    }

    public void removeRowChild(ExpandedCell cell) {
        if (rowDescendants != null) {
            removeFromList(rowDescendants, cell);
            ExpandedCell p = rowParent;
            while (p != null) {
                removeFromList(p.rowDescendants, cell);
                p = p.getRowParent();
            }
        }
    }

    public boolean isRowDescendantOf(ExpandedCell cell) {
        ExpandedCell c = rowParent;
        do {
            if (c == cell)
                return true;
            if (c == null)
                return false;
            c = c.getRowParent();
        } while (true);
    }

    public boolean isColDescendantOf(ExpandedCell cell) {
        ExpandedCell c = colParent;
        do {
            if (c == cell)
                return true;
            if (c == null)
                return false;
            c = c.getColParent();
        } while (true);
    }

    public void setColDescendants(Map<String, List<ExpandedCell>> colDescendants) {
        this.colDescendants = colDescendants;
    }

    public void addRowChildren(Map<String, List<ExpandedCell>> rowChildren) {
        this.rowDescendants = merge(this.rowDescendants, rowChildren);
    }

    static Map<String, List<ExpandedCell>> merge(Map<String, List<ExpandedCell>> mapA, Map<String, List<ExpandedCell>> mapB) {
        if (mapA == null)
            return mapB;

        for (Map.Entry<String, List<ExpandedCell>> entry : mapB.entrySet()) {
            List<ExpandedCell> listB = entry.getValue();
            List<ExpandedCell> listA = mapA.get(entry.getKey());
            if (listA == null) {
                listA = new ArrayList<>(listB);
                mapA.put(entry.getKey(), listA);
            } else {
                listA.addAll(listB);
            }
        }
        return mapA;
    }

    public Map<String, List<ExpandedCell>> getColDescendants() {
        return colDescendants;
    }

    public void addColChildren(Map<String, List<ExpandedCell>> colChildren) {
        this.colDescendants = merge(this.colDescendants, colChildren);
    }

    public ExpandedCellSet rowChildSet(String cellName) {
        String expr = cellName + "[" + getName() + "]";

        List<ExpandedCell> cells = null;
        if (rowDescendants != null) {
            cells = rowDescendants.get(cellName);
        }

        if (cells == null || cells.isEmpty())
            return new ExpandedCellSet(null, expr, Collections.emptyList());
        return new ExpandedCellSet(null, expr, cells);
    }

    public ExpandedCellSet colChildSet(String cellName) {
        String expr = cellName + "[" + getName() + "]";

        List<ExpandedCell> cells = null;
        if (colDescendants != null) {
            cells = colDescendants.get(cellName);
        }

        if (cells == null || cells.isEmpty())
            return new ExpandedCellSet(null, expr, Collections.emptyList());
        return new ExpandedCellSet(null, expr, cells);
    }

    public void changeColSpan(int delta) {
        this.mergeAcross += delta;
    }

    public void changeRowSpan(int delta) {
        this.mergeDown += delta;
    }
}