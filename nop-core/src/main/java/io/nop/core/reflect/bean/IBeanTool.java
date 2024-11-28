/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.core.type.IGenericType;

import java.lang.reflect.Type;

public interface IBeanTool extends IBeanObjectAdapter, IBeanCopier {
    // Object getProperty(Object bean, String propName);

    // void setProperty(Object bean, String propName, Object value);

    // Object makeProperty(Object bean, String propName);
    default boolean isDataBean(Class<?> clazz) {
        return clazz.isAnnotationPresent(DataBean.class);
    }

    IGenericType getGenericType(Type type);

    Object getByIndex(Object bean, int index);

    void setByIndex(Object bean, int index, Object value);

    Object buildBeanFromTreeBean(ITreeBean src, IGenericType targetType, BeanCopyOptions options);

    Object pluckSelected(Object bean, FieldSelectionBean selection);
}