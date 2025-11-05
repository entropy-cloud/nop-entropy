/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.api;

import io.nop.api.core.annotations.core.Label;

public enum EntityChangeType {
    @Label("add")
    A,

    @Label("delete")
    D,

    @Label("update")
    U
}
