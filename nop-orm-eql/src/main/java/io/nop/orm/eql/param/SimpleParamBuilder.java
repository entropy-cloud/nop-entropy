/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.param;

import io.nop.commons.type.StdDataType;

import java.util.List;

public class SimpleParamBuilder implements ISqlParamBuilder {
    private final StdDataType stdDataType;
    private final int paramIndex;

    public SimpleParamBuilder(StdDataType stdDataType, int paramIndex) {
        this.stdDataType = stdDataType;
        this.paramIndex = paramIndex;
    }

    @Override
    public void buildParams(List<Object> input, List<Object> params) {
        Object value = input.get(paramIndex);
        value = stdDataType.convert(value);
        params.add(value);
    }
}