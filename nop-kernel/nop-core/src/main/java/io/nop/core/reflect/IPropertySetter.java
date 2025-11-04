/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.core.lang.eval.IEvalScope;

/**
 * 为减少接口数量，总是传递了propName属性
 */
@FunctionalInterface
public interface IPropertySetter {
    void setProperty(Object obj, String propName, Object value, IEvalScope scope);
}