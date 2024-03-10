/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.param;

import io.nop.api.core.context.ContextProvider;

import java.util.List;

public class TenantParamBuilder implements ISqlParamBuilder {
    public static final TenantParamBuilder INSTANCE = new TenantParamBuilder();

    @Override
    public void buildParams(List<Object> input, List<Object> params) {
        params.add(ContextProvider.currentTenantId());
    }
}