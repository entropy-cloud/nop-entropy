/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.context.IServiceContext;

public interface IServiceActionArgBuilder {
    Object build(Object request, FieldSelectionBean selection, IServiceContext context);
}
