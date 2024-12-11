/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.layout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.IRow;
import io.nop.core.model.table.impl.BaseCell;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@DataBean
public class LayoutGroupModel extends BaseCell implements ILayoutGroupModel, ILayoutCellModel {
    private LayoutTableModel table;

    public String getType() {
        return "group";
    }

    public List<LayoutRowModel> getRows() {
        return table.getRows();
    }

    public void forEachLayoutCell(Consumer<LayoutCellModel> action) {
        table.forEachLayoutCell(action);
    }

    @Override
    public ILayoutCellModel getLayoutCellById(String cellId) {
        if (Objects.equals(getId(), cellId))
            return this;
        return table.getLayoutCellById(cellId);
    }

    @Override
    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);
        out.put("table", table);
    }

    public LayoutTableModel getTable() {
        return table;
    }

    public void setTable(LayoutTableModel table) {
        this.table = table;
    }

    @Override
    public void display(StringBuilder sb, int indent) {
        table.display(sb, !isFirstRealCell(), !isLastRealCell(), indent);
    }

    public boolean isFirstRealCell() {
        IRow row = getRow();
        if (row == null)
            return false;
        return row.getFirstRealCell() == this;
    }

    public boolean isLastRealCell() {
        IRow row = getRow();
        if (row == null)
            return false;
        return row.getLastRealCell() == this;
    }

    @JsonIgnore
    public int getLevel() {
        return table.getLevel();
    }

    @JsonIgnore
    public String getId() {
        if(table == null)
            return null;
        return table.getId();
    }

    @JsonIgnore
    public String getLabel() {
        return table.getLabel();
    }

    @JsonIgnore
    public boolean isFoldable() {
        return table.isFoldable();
    }

    @JsonIgnore
    public boolean isFolded() {
        return table.isFolded();
    }
}