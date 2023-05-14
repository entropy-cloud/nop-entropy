/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.binder;

import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;

public interface IDataParameterBinder {
    StdSqlType getStdSqlType();

    default StdDataType getStdDataType() {
        return getStdSqlType().getStdDataType();
    }

    default Class<?> getJavaClass() {
        return getStdDataType().getJavaClass();
    }

    default int getJdbcType() {
        return getStdSqlType().getJdbcType();
    }

    Object getValue(IDataParameters params, int index);

    void setValue(IDataParameters params, int index, Object value);
}