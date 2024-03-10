/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

public enum ExprPhase {
    /**
     * %{} 在XNode transform阶段用于隔离表达式内容
     */
    transform,

    /**
     * #{}表示编译期需要立刻执行的内容
     */
    compile,

    /**
     * 在表达式执行阶段隔离表达式内容。格式为${}，同时通过#{}格式来支持宏表达式
     */
    eval,

    /**
     * 数据绑定阶段隔离表达式内容，格式为@{}
     */
    binding;
}