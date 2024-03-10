/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;

@Locale("zh-CN")
public enum BatchTransactionScope {
    @Label("不开启事务")
    none,

    @Label("chunk的整个处理阶段")
    chunk,

    @Label("处理和消费阶段")
    process,

    @Label("消费阶段")
    consume;
}