/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.param;

import java.util.List;

public class SimpleParamBuilder implements ISqlParamBuilder {
    private final int paramIndex;

    public SimpleParamBuilder(int paramIndex) {
        this.paramIndex = paramIndex;
    }

    @Override
    public void buildParams(List<Object> input, List<Object> params) {
        Object value = input.get(paramIndex);
        params.add(value);
    }
}