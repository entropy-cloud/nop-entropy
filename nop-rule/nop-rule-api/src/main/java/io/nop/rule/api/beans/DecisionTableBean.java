/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.util.List;

/**
 * 决策表在行和列两个维度上分别对应一个决策树，这两个决策树决定一个行坐标和一个列坐标，由此确定的单元格中记录决策表的输出动作。
 */
@DataBean
public class DecisionTableBean extends ExtensibleBean implements ISourceLocationGetter, ISourceLocationSetter {

    private SourceLocation location;

    private String name;
    private String displayName;
    private String description;

    private TreeRuleBean rowDecider;
    private TreeRuleBean colDecider;

    private List<DecisionTableCellBean> cells;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public TreeRuleBean getRowDecider() {
        return rowDecider;
    }

    public void setRowDecider(TreeRuleBean rowDecider) {
        this.rowDecider = rowDecider;
    }

    public TreeRuleBean getColDecider() {
        return colDecider;
    }

    public void setColDecider(TreeRuleBean colDecider) {
        this.colDecider = colDecider;
    }

    public List<DecisionTableCellBean> getCells() {
        return cells;
    }

    public void setCells(List<DecisionTableCellBean> cells) {
        this.cells = cells;
    }
}
