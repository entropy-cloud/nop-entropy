/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.initialize;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.util.IOrdered;

/**
 * Nop平台的初始化扩展点。全局变量、虚拟文件系统、Ioc都通过此机制实现初始化
 */
@Internal
public interface ICoreInitializer extends IOrdered {
    default boolean isEnabled() {
        return true;
    }

    void initialize();

    default void destroy() {
    }
}