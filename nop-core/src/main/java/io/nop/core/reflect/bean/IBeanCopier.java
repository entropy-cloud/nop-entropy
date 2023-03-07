/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.core.type.IGenericType;

import javax.annotation.Nonnull;

/**
 * 提供对象复制、对象拷贝、对象类型转换功能。 通过options.selection指定字段选择，则可以只处理部分字段。
 */
public interface IBeanCopier {
    /**
     * 根据原型对象src, 构造一个类型为targetType的新对象
     *
     * @param src        原型对象
     * @param targetType 目标对象类型
     * @return 新建的对象
     */
    Object buildBean(@Nonnull Object src, @Nonnull IGenericType targetType, BeanCopyOptions options);

    /**
     * 将原型对象上的属性拷贝到目标对象上。深度拷贝。
     *
     * @param src        原型对象
     * @param target     目标对象
     * @param targetType 目标对象的泛型类型
     * @param deep       深度拷贝会递归复制属性对象，而浅层拷贝则只会调用castBeanToType确保属性值为目标类型。
     */
    void copyBean(@Nonnull Object src, @Nonnull Object target, @Nonnull IGenericType targetType, boolean deep,
                  BeanCopyOptions options);

    /**
     * 将对象转型到指定类型
     *
     * @param src        对象
     * @param targetType 目标类型，支持泛型
     * @param options    附加选项
     * @return 如果src满足泛型要求，则直接返回src，否则创建一个新对象返回
     */
    Object castBeanToType(@Nonnull Object src, @Nonnull IGenericType targetType, BeanCopyOptions options);
}