/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.delta;

public enum DeltaListKind {
    // 所有条目都具有id
    ALL_WITH_ID,
    // 部分条目具有id
    SOME_WITH_ID,

    NONE_WITH_ID
}
