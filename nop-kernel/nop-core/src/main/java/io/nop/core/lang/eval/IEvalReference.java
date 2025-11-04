/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

/**
 * 引用值。反射系统进行类型转换时会自动尝试解引用
 *
 * @param <T>
 */
public interface IEvalReference<T> {
    T getValue();

    void setValue(T value);
}