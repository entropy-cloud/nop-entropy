/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.tree;

public enum TreeVisitResult {
    CONTINUE,

    /**
     * 跳过子节点
     */
    SKIP_CHILD,

    /**
     * 跳过后续的兄弟节点
     */
    SKIP_SIBLINGS,

    /**
     * 处理完毕，不再检查后续节点
     */
    END
}
