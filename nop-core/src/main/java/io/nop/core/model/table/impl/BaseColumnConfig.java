/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table.impl;

import io.nop.core.model.table.IColumnConfig;
import io.nop.core.reflect.hook.SerializableExtensibleObject;

public class BaseColumnConfig extends SerializableExtensibleObject implements IColumnConfig {
    private static final long serialVersionUID = 6006406625695160780L;

    private String styleId;
    private Double width;
    private boolean hidden;

    @Override
    public String getStyleId() {
        return styleId;
    }

    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    @Override
    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}