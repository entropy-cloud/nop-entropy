/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.type.IGenericType;

public interface IFunctionArgument extends IAnnotatedElement {
    String getName();

    IGenericType getType();

    IFunctionArgument cloneInstance();

    default Class<?> getRawClass() {
        return getType().getRawClass();
    }

    default boolean isAssignableFrom(Class<?> clazz) {
        return getType().isAssignableFrom(clazz);
    }

    /**
     * 参数是否允许为null
     */
    boolean isNullable();

    /**
     * 将value转型到当前类型。转型失败则抛出异常
     *
     * @param value 待转型的值
     * @return 转型后的值
     */
    Object castArg(Object value, IEvalScope scope);
}