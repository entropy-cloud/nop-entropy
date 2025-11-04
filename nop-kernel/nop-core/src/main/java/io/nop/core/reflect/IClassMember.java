/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import java.lang.reflect.Modifier;

/**
 * 属性和方法都属于classMember
 */
public interface IClassMember {

    String getName();

    int getModifiers();

    default boolean isPublic() {
        return Modifier.isPublic(getModifiers());
    }

    default boolean isProtected() {
        return Modifier.isProtected(getModifiers());
    }

    default boolean isPrivate() {
        return Modifier.isPrivate(getModifiers());
    }

    default boolean isAbstract() {
        return Modifier.isAbstract(getModifiers());
    }

    default boolean isNative() {
        return Modifier.isNative(getModifiers());
    }

    default boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    default boolean isTransient() {
        return Modifier.isTransient(getModifiers());
    }

    default boolean isSynchronized() {
        return Modifier.isSynchronized(getModifiers());
    }

    default boolean isVolatile() {
        return Modifier.isVolatile(getModifiers());
    }

}