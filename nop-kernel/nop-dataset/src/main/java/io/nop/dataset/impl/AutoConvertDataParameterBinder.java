/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.impl;

import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.binder.IDataParameters;


public class AutoConvertDataParameterBinder implements IDataParameterBinder {
    private final StdDataType dataType;
    private final IDataParameterBinder binder;
    private final ITypeConverter readConverter;
    private final ITypeConverter writeConverter;

    public AutoConvertDataParameterBinder(StdDataType dataType, IDataParameterBinder binder) {
        this.dataType = dataType;
        this.binder = binder;
        this.readConverter = SysConverterRegistry.instance().getConverterByType(dataType.getJavaClass());
        this.writeConverter = SysConverterRegistry.instance()
                .getConverterByType(binder.getStdDataType().getJavaClass());
    }

    @Override
    public StdSqlType getStdSqlType() {
        return binder.getStdSqlType();
    }

    @Override
    public StdDataType getStdDataType() {
        return dataType;
    }

    @Override
    public Object getValue(IDataParameters params, int index) {
        Object value = binder.getValue(params, index);
        if (value == null)
            return null;
        return readConverter.convert(value, NopException::new);
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value != null) {
            value = writeConverter.convert(value, NopException::new);
        }
        binder.setValue(params, index, value);
    }
}
