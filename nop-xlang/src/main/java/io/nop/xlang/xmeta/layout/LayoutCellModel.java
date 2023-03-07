/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.layout;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.table.impl.BaseCell;

@DataBean
public class LayoutCellModel extends BaseCell implements ILayoutCellModel {
    private boolean hideLabel;
    private Boolean readonly;
    private Boolean mandatory;
    private String label;

    public String getType() {
        return "cell";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getValue() {
        return super.getValue();
    }

    public void display(StringBuilder sb, int indent) {
        if (hideLabel)
            sb.append('!');
        if (Boolean.TRUE.equals(mandatory)) {
            sb.append('*');
        }
        if (Boolean.TRUE.equals(readonly)) {
            sb.append('@');
        }
        sb.append(getId());
        if (label != null) {
            sb.append('[').append(StringHelper.escapeJava(label)).append(']');
        }

        if (getMergeAcross() > 0 || getMergeDown() > 0) {
            if (getMergeDown() > 0) {
                sb.append('(').append(getRowSpan()).append(',').append(getColSpan()).append(')');
            } else {
                sb.append('(').append(getColSpan()).append(')');
            }
        }
    }

    @Override
    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);
        if (label != null)
            out.put("label", label);

        if (hideLabel)
            out.put("hideLabel", true);
        if (readonly != null) {
            out.put("readonly", readonly);
        }
        if (mandatory != null) {
            out.put("mandatory", mandatory);
        }
    }

    @Override
    public LayoutCellModel cloneInstance() {
        LayoutCellModel cell = new LayoutCellModel();
        copyTo(cell);
        cell.mandatory = mandatory;
        cell.readonly = readonly;
        cell.hideLabel = hideLabel;
        cell.label = label;
        return cell;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        checkAllowChange();
        this.label = label;
    }

    public boolean isHideLabel() {
        return hideLabel;
    }

    public void setHideLabel(boolean hideLabel) {
        checkAllowChange();
        this.hideLabel = hideLabel;
    }

    public Boolean getReadonly() {
        return readonly;
    }

    public void setReadonly(Boolean readonly) {
        checkAllowChange();
        this.readonly = readonly;
    }

    public Boolean getMandatory() {
        return mandatory;
    }

    public void setMandatory(Boolean mandatory) {
        checkAllowChange();
        this.mandatory = mandatory;
    }
}