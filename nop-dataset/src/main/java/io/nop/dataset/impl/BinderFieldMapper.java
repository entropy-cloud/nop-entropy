/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.binder.IDataParameterBinder;

import java.util.List;

public class BinderFieldMapper implements IFieldMapper {
    private final List<IDataParameterBinder> binders;

    public BinderFieldMapper(List<IDataParameterBinder> binders) {
        this.binders = binders;
    }

    @Override
    public Object getValue(IDataRow row, int index) {
        try {
            if (index >= binders.size())
                return row.getObject(index);

            IDataParameterBinder binder = binders.get(index);
            if (binder == null)
                return row.getObject(index);
            return binder.getValue(row, index);
        } catch (NopException e) {
            e.param("fieldIndex", index).param("fieldName", row.getMeta().getFieldName(index));
            throw e;
        }
    }
}