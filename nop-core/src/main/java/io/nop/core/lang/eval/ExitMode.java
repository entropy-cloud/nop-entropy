/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

public enum ExitMode {
    /**
     * 因为执行continue语句而跳出当前语句
     */
    CONTINUE,

    /**
     * 因为执行break语句而雕出当前语句
     */
    BREAK,

    /**
     * 因为执行return语句而跳出当前语句
     */
    RETURN
}