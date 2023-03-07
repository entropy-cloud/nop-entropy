/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.bean.IBeanConstructor;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanModelManager;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_BEAN_UNKNOWN_PROP;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_GETTER;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_PROP_NOT_READABLE;

public class PropertyAccessorBuilder {
    private final IBeanModelManager beanModelManager;

    public PropertyAccessorBuilder(IBeanModelManager beanModelManager) {
        this.beanModelManager = beanModelManager;
    }

    public PropertyAccessor parseAndBuild(IGenericType beanType, SourceLocation loc, String propPath) {
        if (isSimple(propPath)) {
            IBeanModel beanModel = beanModelManager.getBeanModelForType(beanType);
            return buildBeanPropAccessor(beanModel, beanType, propPath, false, null);
        }

        List<Object> paths = new PropPathParser().parseFromText(loc, propPath);

        IBeanModel beanModel = beanModelManager.getBeanModelForType(beanType);

        PropertyAccessor owner = null;
        for (int i = 0, n = paths.size(); i < n - 1; i++) {
            Object propKey = paths.get(i);

            if (owner != null && owner.getGetter() == null)
                throw new NopException(ERR_REFLECT_BEAN_PROP_NOT_READABLE)
                        .param(ARG_CLASS_NAME, beanModel.getClassName()).param(ARG_PROP_NAME, owner.getPropName());

            PropertyAccessor accessor = buildAccessor(beanModel, beanType, propKey, true, paths.get(i + 1));
            owner = PropertyAccessor.chain(owner, accessor);

            beanType = accessor.getPropType();
            beanModel = beanModelManager.getBeanModelForType(beanType);
        }

        PropertyAccessor accessor = buildAccessor(beanModel, beanType, paths.get(paths.size() - 1), false, null);
        return PropertyAccessor.chain(owner, accessor);
    }

    PropertyAccessor buildAccessor(IBeanModel beanModel, IGenericType beanType, Object propKey, boolean make,
                                   Object nextKey) {

        if (propKey instanceof String) {
            String propName = (String) propKey;
            return buildBeanPropAccessor(beanModel, beanType, propName, make, nextKey);
        } else if (propKey instanceof Integer) {
            IGenericType propType;
            int index = (Integer) propKey;
            if (beanType.isArray()) {
                propType = beanType.getComponentType();
                return buildElementAccessor(String.valueOf(propKey), propType,
                        PropertyAccessorAdapters.getByIndex(ArrayElementAccessor.INSTANCE, index),
                        PropertyAccessorAdapters.setByIndex(ArrayElementAccessor.INSTANCE, index), make);
            } else if (beanType.isListLike()) {
                propType = resolvePropType(beanType, beanModel, beanModel.getComponentType(), nextKey);
                return buildElementAccessor(String.valueOf(propKey), propType,
                        PropertyAccessorAdapters.getByIndex(ListElementAccessor.INSTANCE, index),
                        PropertyAccessorAdapters.setByIndex(ListElementAccessor.INSTANCE, index), make);
            } else if (beanModel.getRawClass() == Object.class) {
                propType = guessType(nextKey);
                return buildElementAccessor(String.valueOf(propKey), propType,
                        PropertyAccessorAdapters.getByIndex(GeneralElementAccessor.INSTANCE, index),
                        PropertyAccessorAdapters.setByIndex(GeneralElementAccessor.INSTANCE, index), make);
            } else {
                throw new NopException(ERR_REFLECT_BEAN_NOT_COLLECTION_FOR_GETTER).param(ARG_CLASS_NAME,
                        beanModel.getClassName());
            }
            // } else {
            // KeyValue pair = (KeyValue) propKey;
            // String propName = pair.getFirst();
            // IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
            // if (propModel != null) {
            // propType = resolvePropType(beanType, beanModel, propModel.getType(), paths, i);
            // components.add(new FilterListPropertyAccessor(propModel, pair.getValue(),
            // getTypeConstructor(propType)));
            // } else if (beanModel.isAllowGetExtProperty() || beanModel.isAllowSetExtProperty()) {
            // propType = guessType(paths, i);
            // components.add(new FilterListPropertyAccessor(new BeanExtPropertyAccessor(beanModel, propName, null),
            // pair.getSecond(), getTypeConstructor(propType)));
            // } else {
            // throw new NopException(ERR_BEAN_UNKNOWN_PROP)
            // .param(ARG_CLASS_NAME, beanModel.getClassName())
            // .param(ARG_PROP_NAME, propName);
            // }
        } else {
            throw new NopException(ERR_BEAN_UNKNOWN_PROP).param(ARG_CLASS_NAME, beanModel.getClassName())
                    .param(ARG_PROP_NAME, String.valueOf(propKey));
        }
    }

    PropertyAccessor buildElementAccessor(String propName, IGenericType propType, IPropertyGetter getter,
                                          IPropertySetter setter, boolean make) {
        IPropertyGetter maker = null;
        if (make) {
            maker = new DefaultPropertyMaker(getter, setter, getTypeConstructor(propType));
        }
        return new PropertyAccessor(propName, getter, setter, maker, propType);
    }

    IBeanConstructor getTypeConstructor(IGenericType beanType) {
        IBeanModel beanModel = beanModelManager.getBeanModelForType(beanType);
        return beanModel::newInstance;
    }

    IGenericType resolvePropType(IGenericType beanType, IBeanModel beanModel, IGenericType propType, Object nextKey) {
        propType = propType.refine(beanModel.getType(), beanType);
        if (propType.getRawClass() == Object.class)
            return guessType(nextKey);
        return propType;
    }

    // 根据后续属性格式来确定当前属性是否是Map或者List类型
    IGenericType guessType(Object propKey) {
        if (propKey instanceof String)
            return beanModelManager.getBeanModelForClass(HashMap.class).getType();
        return beanModelManager.getBeanModelForClass(ArrayList.class).getType();
    }

    boolean isSimple(String text) {
        if (text.indexOf('.') < 0 && text.indexOf('[') < 0)
            return true;
        return false;
    }

    PropertyAccessor buildBeanPropAccessor(IBeanModel beanModel, IGenericType beanType, String propName, boolean make,
                                           Object nextKey) {
        IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
        IGenericType propType = null;
        if (propModel != null) {
            propType = resolvePropType(beanType, beanModel, propModel.getType(), nextKey);
        }

        if (propModel != null) {
            IPropertyGetter getter = propModel.getGetter();
            IPropertySetter setter = propModel.getSetter();
            IPropertyGetter maker = propModel.getMaker();

            if (getter == null)
                getter = beanModel.getExtPropertyGetter();
            if (setter == null)
                setter = beanModel.getExtPropertySetter();

            return new PropertyAccessor(propName, getter, setter, maker, propType);
        } else {
            IPropertyGetter getter = beanModel.getExtPropertyGetter();
            IPropertySetter setter = beanModel.getExtPropertySetter();
            IPropertyGetter maker = null;
            if (getter != null || setter != null) {
                if (propType == null)
                    propType = PredefinedGenericTypes.ANY_TYPE;
                if (make) {
                    if (beanModel.isMapLike()) {
                        propType = beanModel.getType().getMapValueType();
                        maker = new DefaultPropertyMaker(getter, setter, getTypeConstructor(propType));
                    }
                }
                return new PropertyAccessor(propName, getter, setter, maker, propType);
            }
        }
        throw new NopException(ERR_BEAN_UNKNOWN_PROP).param(ARG_CLASS_NAME, beanModel.getClassName())
                .param(ARG_PROP_NAME, propName);
    }

}
