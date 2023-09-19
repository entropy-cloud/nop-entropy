/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.type.IGenericType;

import jakarta.annotation.Nonnull;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_INDEX;
import static io.nop.core.CoreErrors.ARG_LENGTH;
import static io.nop.core.CoreErrors.ERR_BEAN_SET_BY_INDEX_NOT_IN_RANGE;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NOT_SUPPORT_GET_BY_INDEX;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NOT_SUPPORT_SET_BY_INDEX;

public class BeanToolImpl implements IBeanTool {
    private final IBeanModelManager beanModelManager;

    public BeanToolImpl(IBeanModelManager beanModelManager) {
        this.beanModelManager = beanModelManager;
    }

    public Object getByIndex(Object bean, int index) {
        Class<?> clazz = bean.getClass();
        if (clazz.isArray()) {
            int len = Array.getLength(bean);
            if (index < 0 || index >= len)
                return null;
            return Array.get(bean, index);
        } else {
            IBeanModel beanModel = getBeanModel(bean);
            if (beanModel.isListLike()) {
                List<?> list = (List<?>) bean;
                if (index < 0 || index >= list.size())
                    return null;
                return list.get(index);
            } else if (beanModel.isMapLike()) {
                return ((Map) bean).get(index);
            } else {
                throw new NopException(ERR_REFLECT_BEAN_NOT_SUPPORT_GET_BY_INDEX).param(ARG_CLASS_NAME,
                        bean.getClass().getName());
            }
        }
    }

    @Override
    public void setByIndex(Object bean, int index, Object value) {
        Class<?> clazz = bean.getClass();
        if (clazz.isArray()) {
            int len = Array.getLength(bean);
            if (index < 0 || index >= len)
                throw new NopException(ERR_BEAN_SET_BY_INDEX_NOT_IN_RANGE).param(ARG_INDEX, index).param(ARG_LENGTH,
                        len);
            Array.set(bean, index, value);
        } else {
            IBeanModel beanModel = getBeanModel(bean);
            if (beanModel.isListLike()) {
                List<Object> list = (List<Object>) bean;
                if (index < 0 || index >= list.size())
                    throw new NopException(ERR_BEAN_SET_BY_INDEX_NOT_IN_RANGE).param(ARG_INDEX, index).param(ARG_LENGTH,
                            list.size());

                list.set(index, value);
            } else if (beanModel.isMapLike()) {
                ((Map) bean).put(index, value);
            } else {
                throw new NopException(ERR_REFLECT_BEAN_NOT_SUPPORT_SET_BY_INDEX).param(ARG_CLASS_NAME,
                        bean.getClass().getName());
            }
        }
    }

    @Override
    public IGenericType getGenericType(Type type) {
        return beanModelManager.buildGenericType(type);
    }

    IBeanModel getBeanModel(Object bean) {
        return beanModelManager.getBeanModelForClass(bean.getClass());
    }

    @Override
    public Object getProperty(Object bean, String propName, IEvalScope scope) {
        if (propName.charAt(0) == '@') {
            // 针对csv-set的一个特殊语法。 tagSet.@published等价于 tagSet.contains('published')
            Class<?> clazz = bean.getClass();
            if (clazz == LinkedHashSet.class || clazz == HashSet.class) {
                return ((Set) bean).contains(propName.substring(1));
            }
        }
        return getBeanModel(bean).getProperty(bean, propName, scope);
    }

    @Override
    public Object makeProperty(Object bean, String propName, IEvalScope scope) {
        return getBeanModel(bean).makeProperty(bean, propName, scope);
    }

    @Override
    public void setProperty(Object bean, String propName, Object value, IEvalScope scope) {
        getBeanModel(bean).setProperty(bean, propName, value, scope);
    }

    @Override
    public boolean hasProperty(Object bean, String propName) {
        if (bean instanceof Map)
            return ((Map<?, ?>) bean).containsKey(propName);

        IBeanModel beanModel = getBeanModel(bean);
        if (beanModel.getPropertyModel(propName) != null)
            return true;

        if (bean instanceof IPropGetMissingHook)
            return ((IPropGetMissingHook) bean).prop_has(propName);

        return false;
    }

    @Override
    public Object buildBean(@Nonnull Object src, @Nonnull IGenericType targetType, BeanCopyOptions options) {
        return BeanCopier.INSTANCE.buildBean(src, targetType, options);
    }

    @Override
    public void copyBean(@Nonnull Object src, @Nonnull Object target, @Nonnull IGenericType targetType, boolean deep,
                         BeanCopyOptions options) {
        BeanCopier.INSTANCE.copyBean(src, target, targetType, deep, options);
    }

    @Override
    public Object castBeanToType(@Nonnull Object src, @Nonnull IGenericType targetType, BeanCopyOptions options) {
        return BeanCopier.INSTANCE.castBeanToType(src, targetType, options);
    }

    @Override
    public Object buildBeanFromTreeBean(ITreeBean src, IGenericType targetType, BeanCopyOptions options) {
        return TreeBeanBuilder.INSTANCE.buildBeanFromTreeBean(src, targetType, options);
    }
}
