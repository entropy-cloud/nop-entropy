/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.param;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.List;

import static io.nop.core.CoreErrors.ERR_CORE_NO_TENANT_ID;

public class TenantParamBuilder implements ISqlParamBuilder {
    public static final TenantParamBuilder INSTANCE = new TenantParamBuilder();

    @Override
    public void buildParams(List<Object> input, List<Object> params) {
        String tenantId = ContextProvider.currentTenantId();
        if (StringHelper.isEmpty(tenantId))
            throw new NopException(ERR_CORE_NO_TENANT_ID);

        params.add(tenantId);
    }
}