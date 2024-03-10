/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

/**
 * 对于IPropertyGetter和IPropertySetter，如果内部实现已经确定了propName，则可以实现这个接口，表示忽略传入的propName参数
 */
public interface ISpecializedAccessor {
}
