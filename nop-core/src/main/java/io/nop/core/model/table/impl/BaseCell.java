/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.impl;

import io.nop.core.lang.json.IJsonHandler;

public class BaseCell extends AbstractCell {

    private static final long serialVersionUID = 1L;

    private String comment;
    private Object value;

    protected void copyTo(BaseCell cell) {
        super.copyTo(cell);
        cell.value = value;
        cell.comment = comment;
    }

    @Override
    public String getFormula() {
        return null;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment){
        this.comment = comment;
    }

    @Override
    protected void outputJson(IJsonHandler out) {
        super.outputJson(out);
        if (isProxyCell()) {
            out.put("proxy", true);
        } else {
            if (value != null)
                out.put("value", value);
            if (comment != null)
                out.put("comment", comment);
        }
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void setValue(Object value) {
        checkAllowChange();
        this.value = value;
    }

    @Override
    public BaseCell cloneInstance() {
        BaseCell ret = new BaseCell();
        copyTo(ret);
        return ret;
    }
}