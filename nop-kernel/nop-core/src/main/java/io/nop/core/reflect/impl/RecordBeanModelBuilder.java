/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.accessor.MethodPropertyGetter;
import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.BeanPropertyModel;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.reflect.bean.MethodBeanConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RecordBeanModelBuilder {

    private static final Method GET_RECORD_COMPONENTS;

    static {
        Method m = null;
        try {
            m = Class.class.getMethod("getRecordComponents");
        } catch (NoSuchMethodException e) {
            // Java < 16
        }
        GET_RECORD_COMPONENTS = m;
    }

    public IBeanModel buildFromClassModel(IClassModel classModel) {
        BeanModel beanModel = new BeanModel();
        beanModel.setType(classModel.getType());
        beanModel.setDescription(classModel.getDescription());
        beanModel.setImmutable(true);
        beanModel.setDataBean(true);

        Class<?> rawClass = classModel.getRawClass();
        Object[] components = getRecordComponents(rawClass);

        buildProps(beanModel, classModel, components);
        buildConstructor(beanModel, rawClass, components);

        return beanModel;
    }

    private Object[] getRecordComponents(Class<?> rawClass) {
        if (GET_RECORD_COMPONENTS == null)
            throw new UnsupportedOperationException("Record types are not supported on this Java version");
        try {
            return (Object[]) GET_RECORD_COMPONENTS.invoke(rawClass);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get record components for: " + rawClass.getName(), e);
        }
    }

    private static String getComponentName(Object rc) {
        try {
            Method getName = rc.getClass().getMethod("getName");
            return (String) getName.invoke(rc);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get record component name", e);
        }
    }

    private void buildProps(BeanModel beanModel, IClassModel classModel, Object[] components) {
        Map<String, IBeanPropertyModel> props = new TreeMap<>();

        for (Object rc : components) {
            String propName = getComponentName(rc);
            IFunctionModel getter = classModel.getMethodByExactType(propName);
            if (getter == null)
                throw new IllegalStateException("Record component accessor method not found: " + propName);

            BeanPropertyModel propModel = new BeanPropertyModel();
            propModel.setName(propName);
            propModel.setType(getter.getReturnType());
            propModel.setGetter(new MethodPropertyGetter(getter));
            propModel.setSerializable(true);
            props.put(propName, propModel);
        }

        beanModel.setPropertyModels(CollectionHelper.immutableSortedMap(props));
        beanModel.setPropAliases(Collections.emptyMap());
    }

    private void buildConstructor(BeanModel beanModel, Class<?> rawClass, Object[] components) {
        int componentCount = components.length;
        Constructor<?> canonicalCtor = findCanonicalConstructor(rawClass, componentCount);
        if (canonicalCtor == null)
            throw new IllegalStateException("Canonical constructor not found for record: " + rawClass.getName());

        FunctionModel ctorModel = MethodModelBuilder.from(rawClass, canonicalCtor);
        MethodBeanConstructor ctor = new MethodBeanConstructor(ctorModel);

        List<String> propNames = new ArrayList<>(componentCount);
        for (Object rc : components) {
            propNames.add(getComponentName(rc));
        }

        beanModel.setConstructorPropNames(propNames);
        beanModel.setConstructorEx(ctor);

        if (componentCount == 0) {
            beanModel.setConstructor(ctor);
        }
    }

    private Constructor<?> findCanonicalConstructor(Class<?> rawClass, int componentCount) {
        for (Constructor<?> ctor : rawClass.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == componentCount) {
                return ctor;
            }
        }
        return null;
    }
}
