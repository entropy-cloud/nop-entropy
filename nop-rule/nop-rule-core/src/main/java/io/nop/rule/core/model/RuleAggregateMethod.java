/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.model;

import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Option;

@Locale("zh-CN")
public enum RuleAggregateMethod {
    @Label("取最小值")
    min,

    @Label("取最大值")
    max,

    @Label("求和")
    sum,

    @Label("取平均值")
    avg,

    @Label("取第一个值")
    first,

    @Label("取最后一个值")
    last,

    @Label("汇总为列表")
    list;
}