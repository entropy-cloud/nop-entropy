/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.biz;

@SuppressWarnings("java:S115")
public enum BizActionArgKind {
    RequestBean,
    ContextSource,
    ContextRoot,
    FieldSelection,
    ServiceContext,
    Cache
}
