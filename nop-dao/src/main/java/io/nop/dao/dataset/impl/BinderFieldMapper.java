/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dataset.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.binder.IDataParameterBinder;
import io.nop.dao.dataset.IDataRow;
import io.nop.dao.dataset.IFieldMapper;

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