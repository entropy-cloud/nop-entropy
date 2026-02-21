/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

/**
 * 变更类型
 */
public enum DiffDeltaType {
    /**
     * 插入新行
     */
    INSERT,

    /**
     * 删除行
     */
    DELETE,

    /**
     * 修改行（删除+插入）
     */
    CHANGE
}
