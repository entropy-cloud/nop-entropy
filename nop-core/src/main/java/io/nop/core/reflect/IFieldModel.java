/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.type.IGenericType;

public interface IFieldModel extends IAnnotatedElement, IClassMember {
    boolean isReadable();

    boolean isWritable();

    default boolean isEnumConstant() {
        return (getModifiers() & 0x00004000) != 0;
    }

    /**
     * 是否扩展属性。数组和列表具有公开的扩展属性length
     */
    boolean isExtension();

    String getName();

    Class<?> getRawClass();

    IGenericType getType();

    IPropertyGetter getGetter();

    IPropertySetter getSetter();

    Object getDefaultValue();

    default Object getValue(Object obj) {
        IPropertyGetter getter = getGetter();
        return getter.getProperty(null, getName(), DisabledEvalScope.INSTANCE);
    }
}