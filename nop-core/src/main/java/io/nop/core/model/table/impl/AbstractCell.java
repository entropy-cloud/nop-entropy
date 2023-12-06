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
    private IRow row;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cell[");
        if (id != null)
            sb.append("id=").append(id).append(",");
        sb.append("text=").append(getText());
        if (this.getMergeAcross() > 0)
            sb.append(",colSpan=").append(getColSpan());
        if (this.getMergeDown() > 0)
            sb.append(",rowSpan=").append(getRowSpan());

        appendInfo(sb);
        sb.append("]");
        return sb.toString();
    }

    protected void appendInfo(StringBuilder sb) {
    }

    protected void copyTo(AbstractCell cell) {
        cell.id = id;
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
    }
}