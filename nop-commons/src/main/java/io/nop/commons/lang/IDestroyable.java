/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang;

/**
 * 类似于Closable, 用于标记非IO对象的销毁函数
 */
public interface IDestroyable {

    default boolean isDestroyed() {
        return false;
    }

    void destroy();
}
