/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.query;

public enum FilterOpType {
    /**
     * 比较算子，要求name和value参数
     */
    COMPARE_OP,

    /**
     * 断言算子，要求name参数
     */
    ASSERT_OP,

    /**
     * 范围算子，要求min,max,excludeMin,excludeMax等参数
     */
    BETWEEN_OP,

    /**
     * 分组算子，不需要参数，包括and,or和not
     */
    GROUP_OP,

    /**
     * alwaysTrue和alwaysFalse这种返回固定值的情况
     */
    FIXED_VALUE,

    /**
     * 扩展算子
     */
    OTHER,
}