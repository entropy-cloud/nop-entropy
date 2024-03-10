/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_TYPE_VALUE;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NO_CLASS_FOR_TYPE;

public class TreeBeanBuilder {
    public static TreeBeanBuilder INSTANCE = new TreeBeanBuilder();

    public Object buildBeanFromTreeBean(ITreeBean src, IGenericType targetType, BeanCopyOptions options) {
        if (options == null)
            options = BeanCopyOptions.DEFAULT;

        if (targetType == PredefinedGenericTypes.X_NODE_TYPE) {
            XNode node = XNode.fromTreeBean(src);
            return node;
        }

        if (ITreeBean.class.isAssignableFrom(targetType.getRawClass())) {
            return src.toTreeBean();
        }

        IBeanModel beanModel = getTargetBeanModel(src, targetType, options);
        Object bean = beanModel.newInstance();
        _copyBean(src, bean, beanModel, targetType, options);
        return bean;
    }

    private IBeanModel getTargetBeanModel(ITreeBean src, IGenericType targetType, BeanCopyOptions options) {
        IBeanModelManager beanModelManager = options.getBeanModelManager();
        IBeanModel beanModel = beanModelManager.getBeanModelForType(targetType);
        String typeProp = beanModel.getSubTypeProp();
        if (typeProp != null) {
            String typeValue = src.getTagName();
            IGenericType concreteType = beanModel.determineSubType(typeValue);
            if (concreteType == null)
                throw new NopException(ERR_REFLECT_BEAN_NO_CLASS_FOR_TYPE)
                        .param(ARG_CLASS_NAME, beanModel.getClassName()).param(ARG_TYPE_VALUE, typeValue);
            beanModel = beanModelManager.getBeanModelForType(concreteType);
        }
        return beanModel;
    }

    private void _copyBean(ITreeBean src, Object bean, IBeanModel beanModel, IGenericType beanType,
                           BeanCopyOptions options) {
        Map<String, Object> attrs = src.getAttrs();
        if (attrs != null) {
            attrs.forEach((key, value) -> {
                BeanCopier.INSTANCE.setFieldValue(beanModel, bean, key, value, beanType, true, null, options);
            });
        }

        List<? extends ITreeBean> children = src.getChildren();
        if (children != null) {
            for (ITreeBean child : children) {
                String tagName = child.getTagName();
                copyBeanProp(child, bean, beanModel, tagName, beanType, options);
            }
        }

        Object contentValue = src.getContentValue();
        if (contentValue != null) {
            BeanCopier.INSTANCE.setFieldValue(beanModel, bean, src.getTagName(), contentValue, beanType, true, null,
                    options);
        }
    }

    private void copyBeanProp(ITreeBean src, Object bean, IBeanModel beanModel, String propName, IGenericType beanType,
                              BeanCopyOptions options) {
        IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
        if (propModel != null) {
            IGenericType propType = propModel.getType().refine(beanModel.getType(), beanType);
            Object value;
            if (propModel.getType().isCollectionLike()) {
                value = buildCollectionValue(src, propType, options);
            } else {
                value = buildBeanFromTreeBean(src, propType, options);
            }
            propModel.setPropertyValue(bean, value);
        } else {
            beanModel.setExtProperty(bean, propName, src.toJsonObject());
        }
    }

    private List<Object> buildCollectionValue(ITreeBean src, IGenericType propType, BeanCopyOptions options) {
        List<? extends ITreeBean> children = src.getChildren();
        if (children == null)
            return null;
        if (children.isEmpty())
            return new ArrayList<>(0);

        List<Object> ret = new ArrayList<>(children.size());
        for (ITreeBean child : children) {
            Object value = buildBeanFromTreeBean(child, propType.getComponentType(), options);
            ret.add(value);
        }
        return ret;
    }
}