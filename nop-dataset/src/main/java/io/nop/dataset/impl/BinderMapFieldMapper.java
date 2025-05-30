package io.nop.dataset.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.binder.IDataParameterBinder;

import java.util.Map;

public class BinderMapFieldMapper implements IFieldMapper {
    private final Map<String, IDataParameterBinder> binders;

    public BinderMapFieldMapper(Map<String, IDataParameterBinder> binders) {
        this.binders = binders;
    }

    @Override
    public Object getValue(IDataRow row, int index) {
        try {
            String name = row.getMeta().getFieldName(index);
            IDataParameterBinder binder = binders.get(name);
            if (binder == null)
                return row.getObject(index);
            return binder.getValue(row, index);
        } catch (NopException e) {
            e.param("fieldIndex", index).param("fieldName", row.getMeta().getFieldName(index));
            throw e;
        }
    }
}