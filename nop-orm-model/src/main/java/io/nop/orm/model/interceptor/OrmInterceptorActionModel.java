/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model.interceptor;

import io.nop.api.core.util.IOrdered;
import io.nop.orm.model.interceptor._gen._OrmInterceptorActionModel;

public class OrmInterceptorActionModel extends _OrmInterceptorActionModel implements IOrdered {
    public OrmInterceptorActionModel() {

    }

    @Override
    public int order() {
        return getOrder();
    }
}
