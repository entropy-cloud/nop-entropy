/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.layout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.component.AbstractFreezable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@DataBean
public class LayoutModel extends AbstractFreezable implements IComponentModel {
    private List<LayoutTableModel> groups = Collections.emptyList();

    public List<LayoutTableModel> getGroups() {
        return groups;
    }

    public void setGroups(List<LayoutTableModel> groups) {
        checkAllowChange();
        this.groups = groups == null ? Collections.emptyList() : groups;
    }

    public void forEachLayoutCell(Consumer<LayoutCellModel> consumer) {
        for (LayoutTableModel table : getGroups()) {
            table.forEachLayoutCell(consumer);
        }
    }

    @JsonIgnore
    public List<LayoutCellModel> getLayoutCells() {
        List<LayoutCellModel> ret = new ArrayList<>();
        forEachLayoutCell(ret::add);
        return ret;
    }

    @JsonIgnore
    public boolean isSimpleTable() {
        return groups.size() == 1 && groups.get(0).isSimple();
    }

    @JsonIgnore
    public LayoutTableModel getFirstTable() {
        if (groups.isEmpty())
            return null;
        return groups.get(0);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        display(sb, 0);
        return sb.toString();
    }

    public void display(StringBuilder sb, int indent) {
        for (LayoutTableModel table : groups) {
            table.display(sb, false, false, indent);
        }
    }
}