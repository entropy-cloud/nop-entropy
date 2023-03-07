/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.diff;

public enum DiffType {
    same,

    /**
     * 在集合中不存在，因此是新增
     */
    add,

    /**
     * 在集合中不存在
     */
    remove,

    /**
     * 内部属性部分更新
     */
    update,

    /**
     * 原先也存在，替换
     */
    replace
}