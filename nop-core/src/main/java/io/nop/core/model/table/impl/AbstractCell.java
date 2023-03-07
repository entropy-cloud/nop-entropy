/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.IRow;
import io.nop.core.resource.component.AbstractComponentModel;

public abstract class AbstractCell extends AbstractComponentModel implements ICell, IJsonSerializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String styleId;
    private int mergeAcross;
    private int mergeDown;
    private IRow row;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cell[");
        if (id != null)
            sb.append("id=").append(id).append(",");
        sb.append("text=").append(getText());
        if (this.mergeAcross > 0)
            sb.append(",colSpan=").append(getColSpan());
        if (this.mergeDown > 0)
            sb.append(",rowSpan=").append(getRowSpan());

        appendInfo(sb);
        sb.append("]");
        return sb.toString();
    }

    protected void appendInfo(StringBuilder sb) {
    }

    protected void copyTo(AbstractCell cell) {
        cell.id = id;
        cell.mergeAcross = mergeAcross;
        cell.mergeDown = mergeDown;
    }

    public String getId() {
        return id;
    }

    public void setId(String cellId) {
        checkAllowChange();
        this.id = cellId;
    }

    @Override
    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        checkAllowChange();
        this.styleId = styleId;
    }

    @Override
    public void setMergeAcross(int mergeAcross) {
        this.mergeAcross = mergeAcross;
    }

    @Override
    public void setMergeDown(int mergeDown) {
        checkAllowChange();
        this.mergeDown = mergeDown;
    }

    public int getMergeAcross() {
        return mergeAcross;
    }

    public int getMergeDown() {
        return mergeDown;
    }

    @JsonIgnore
    @Override
    public IRow getRow() {
        return row;
    }

    @Override
    public void setRow(IRow row) {
        checkAllowChange();
        this.row = row;
    }

    @Override
    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);
        if (id != null)
            out.put("id", id);
        if (styleId != null)
            out.put("styleId", styleId);
        if (mergeAcross > 0)
            out.put("mergeAcross", mergeAcross);
        if (mergeDown > 0)
            out.put("mergeDown", mergeDown);
    }
}