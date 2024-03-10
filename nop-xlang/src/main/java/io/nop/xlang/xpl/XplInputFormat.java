/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

public enum XplInputFormat {
    /**
     * 缺省情况，允许表达式混合
     */
    mixed,

    /**
     * 仅允许不含表达式的文本或者编译期表达式
     */
    value,

    /**
     * 必须是单一表达式
     */
    expr;
}