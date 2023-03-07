/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

/**
 * 调用freeze函数之后对象成为不可变对象。一旦进入不可变状态就不可能再被修改
 */
public interface IFreezable {
    boolean frozen();

    /**
     * 标记对象被冻结
     *
     * @param cascade 是否递归冻结子对象
     */
    void freeze(boolean cascade);
}
