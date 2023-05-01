/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.tree;

import io.nop.api.core.util.Guard;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;
import io.nop.core.model.tree.ITreeStructure;
import io.nop.core.resource.component.AbstractFreezable;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述节点以及子节点的相对位置，占据的单元格空间大小等
 */
public class TreeCell extends AbstractFreezable implements ITreeStructure, ICell {
    private static final long serialVersionUID = -5847054269512519030L;

    private String id;
    private String styleId;
    private Object model;
    private Object value;
    private String comment;
    private final TreeCellChildPosition childPos;

    private TreeCell parent;
    private List<TreeCell> children;
    private IRow row;

    /**
     * 父节点以及所有子节点构成的区域整体的colSpan。由calcBbox函数负责初步计算, 由adjustBbox负责修正。
     */
    private int bboxWidth;
    private int bboxHeight;

    private int mergeAcross;
    private int mergeDown;
    private int rowIndex = -1;
    private int colIndex = -1;

    /**
     * 树节点所处的层次，顶层的treeLevel为0，子层加1
     */
    private int treeLevel;

    /**
     * 最底层的叶子节点的序号，从0开始
     */
    private int leafIndex;

    public TreeCell(Object value, TreeCellChildPosition pos) {
        this.value = value;
        this.childPos = pos;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    public Object getModel() {
        return model;
    }

    public void setModel(Object model) {
        this.model = model;
    }

    @Override
    public TreeCell cloneInstance() {
        TreeCell ret = new TreeCell(value, childPos);
        ret.id = id;
        ret.model = model;
        ret.styleId = styleId;
        ret.comment = comment;
        ret.bboxWidth = bboxWidth;
        ret.bboxHeight = bboxHeight;
        ret.mergeAcross = mergeAcross;
        ret.mergeDown = mergeDown;
        ret.rowIndex = rowIndex;
        ret.colIndex = colIndex;
        ret.leafIndex = leafIndex;
        ret.treeLevel = treeLevel;
        return ret;
    }

    /**
     * 虚拟节点本身不占据空间，它所占据的空间由子节点决定，如果子节点为空，则实际上该节点可以被删除。
     */
    public boolean isVirtual() {
        return childPos == TreeCellChildPosition.hor || childPos == TreeCellChildPosition.ver;
    }

    public int getLeafIndex() {
        return leafIndex;
    }

    public void setLeafIndex(int leafIndex) {
        this.leafIndex = leafIndex;
    }

    public void addChild(TreeCell cell) {
        Guard.checkArgument(cell.getParent() == null);
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        cell.setParent(this);
        this.children.add(cell);
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public TreeCell getParent() {
        return parent;
    }

    public void setParent(TreeCell parent) {
        this.parent = parent;
    }

    @Override
    public List<TreeCell> getChildren() {
        return children;
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public void setChildren(List<TreeCell> children) {
        this.children = children;
    }

    public int getColSpan() {
        return mergeAcross + 1;
    }

    public int getRowSpan() {
        return mergeDown + 1;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public TreeCellChildPosition getChildPos() {
        return childPos;
    }

    public void setBboxWidth(int bboxWidth) {
        this.bboxWidth = bboxWidth;
    }

    public void setBboxHeight(int bboxHeight) {
        this.bboxHeight = bboxHeight;
    }

    public int getMergeAcross() {
        return mergeAcross;
    }

    public void setMergeAcross(int mergeAcross) {
        this.mergeAcross = mergeAcross;
    }

    public int getMergeDown() {
        return mergeDown;
    }

    public void setMergeDown(int mergeDown) {
        this.mergeDown = mergeDown;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    public int getEndColIndex() {
        return colIndex + getColSpan();
    }

    public int getEndRowIndex() {
        return rowIndex + getRowSpan();
    }

    public int getTreeLevel() {
        return treeLevel;
    }

    public void setTreeLevel(int treeLevel) {
        this.treeLevel = treeLevel;
    }

    public int getBboxWidth() {
        return bboxWidth;
    }

    public int getBboxHeight() {
        return bboxHeight;
    }

    public void incWidth(int deltaW) {
        this.mergeAcross += deltaW;
        this.bboxWidth += deltaW;
    }

    public void incHeight(int deltaH) {
        this.mergeDown += deltaH;
        this.bboxHeight += deltaH;
    }

    @Override
    public IRow getRow() {
        return row;
    }

    public void setRow(IRow row) {
        this.row = row;
    }
}