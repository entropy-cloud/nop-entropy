/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.collections.iterator.ArrayIterator;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.ReadonlyModel;
import io.nop.core.reflect.IExtPropertyGetter;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IPropertyGetter;
import io.nop.core.reflect.IPropertySetter;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IGenericTypeBuilder;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_NO_DEFAULT_CONSTRUCTOR;

public class BeanModel extends ReadonlyModel implements IBeanModel {
    private IGenericType type;
    private IFunctionModel factoryMethod;
    private String description;
    private boolean immutable;
    private boolean dataBean;

    private String serializer;
    private String deserializer;
    private List<String> constructorPropNames = Collections.emptyList();
    private IBeanConstructor constructor;
    private IBeanConstructorEx constructorEx;

    private String typeProp;
    private Map<String, IGenericType> typeMap = Collections.emptyMap();
    private Map<String, IBeanPropertyModel> propertyModels = Collections.emptyMap();
    private Function<String, IFunctionModel> builderMethodProvider;
    private IExtPropertyGetter extPropertyGetter;
    private IPropertySetter extPropertySetter;
    private IPropertyGetter extPropertyMaker;
    private IGenericTypeBuilder genericTypeResolver = ReflectionManager.instance();
    private Map<String, String> propAliases;

    public IGenericTypeBuilder getGenericTypeResolver() {
        return genericTypeResolver;
    }

    public void setGenericTypeResolver(IGenericTypeBuilder genericTypeResolver) {
        checkReadonly();
        this.genericTypeResolver = genericTypeResolver;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public IGenericType getType() {
        return type;
    }

    public void setType(IGenericType type) {
        checkReadonly();
        this.type = type;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        checkReadonly();
        this.immutable = immutable;
    }

    public boolean hasConstructor() {
        return constructor != null || constructorEx != null;
    }

    @Override
    public boolean isDataBean() {
        return dataBean;
    }

    public void setDataBean(boolean dataBean) {
        checkReadonly();
        this.dataBean = dataBean;
    }

    @Override
    public String getSerializer() {
        return serializer;
    }

    public void setSerializer(String serializer) {
        checkReadonly();
        this.serializer = serializer;
    }

    @Override
    public String getDeserializer() {
        return deserializer;
    }

    public void setDeserializer(String deserializer) {
        checkReadonly();
        this.deserializer = deserializer;
    }

    @Override
    public List<String> getConstructorPropNames() {
        return constructorPropNames;
    }

    public void setConstructorPropNames(List<String> constructorPropNames) {
        checkReadonly();
        this.constructorPropNames = constructorPropNames;
    }

    public IBeanConstructor getConstructor() {
        return constructor;
    }

    public void setConstructor(IBeanConstructor constructor) {
        checkReadonly();
        this.constructor = constructor;
    }

    public IBeanConstructorEx getConstructorEx() {
        return constructorEx;
    }

    public void setConstructorEx(IBeanConstructorEx constructorEx) {
        checkReadonly();
        this.constructorEx = constructorEx;
    }

    public Map<String, String> getPropAliases() {
        return propAliases;
    }

    public void setPropAliases(Map<String, String> propAliases) {
        checkReadonly();
        this.propAliases = propAliases;
    }

    @Override
    public String getSubTypeProp() {
        return typeProp;
    }

    public void setTypeProp(String typeProp) {
        checkReadonly();
        this.typeProp = typeProp;
    }

    @Override
    public IFunctionModel getFactoryMethod() {
        return factoryMethod;
    }

    public void setFactoryMethod(IFunctionModel factoryMethod) {
        this.factoryMethod = factoryMethod;
    }

    public Map<String, IGenericType> getTypeMap() {
        return typeMap;
    }

    public void setTypeMap(Map<String, IGenericType> typeMap) {
        checkReadonly();
        this.typeMap = typeMap;
    }

    public Map<String, IBeanPropertyModel> getPropertyModels() {
        return propertyModels;
    }

    public void setPropertyModels(Map<String, IBeanPropertyModel> propertyModels) {
        checkReadonly();
        this.propertyModels = propertyModels;
    }

    public Function<String, IFunctionModel> getBuilderMethodProvider() {
        return builderMethodProvider;
    }

    public void setBuilderMethodProvider(Function<String, IFunctionModel> builderMethodProvider) {
        checkReadonly();
        this.builderMethodProvider = builderMethodProvider;
    }

    public IExtPropertyGetter getExtPropertyGetter() {
        return extPropertyGetter;
    }

    public void setExtPropertyGetter(IExtPropertyGetter extPropertyGetter) {
        checkReadonly();
        this.extPropertyGetter = extPropertyGetter;
    }

    public IPropertySetter getExtPropertySetter() {
        return extPropertySetter;
    }

    public void setExtPropertySetter(IPropertySetter extPropertySetter) {
        checkReadonly();
        this.extPropertySetter = extPropertySetter;
    }

    @Override
    public Class<?> getComponentType(Object bean) {
        return this.type.getComponentType().getRawClass();
    }

    @Override
    public Object newInstance() {
        if (constructor == null) {
            throw new NopException(ERR_REFLECT_BEAN_NO_DEFAULT_CONSTRUCTOR).param(ARG_CLASS_NAME, getClassName());
        }
        return constructor.newInstance();
    }

    @Override
    public Object newInstance(Object[] args) {
        if (args == null || args.length <= 0)
            return newInstance();
        Guard.notNull(constructorEx, "null constructorEx");
        return constructorEx.newInstance(args);
    }

    @Override
    public IGenericType determineSubType(String typeValue) {
        return typeMap.get(typeValue);
    }

    @Override
    public boolean isAllowGetExtProperty() {
        return extPropertyGetter != null;
    }

    @Override
    public boolean isAllowSetExtProperty() {
        return extPropertySetter != null;
    }

    @Override
    public boolean isAllowMakeExtProperty() {
        return extPropertyMaker != null;
    }

    public void setExtPropertyMaker(IPropertyGetter extPropertyMaker) {
        this.extPropertyMaker = extPropertyMaker;
    }

    @Override
    public IBeanPropertyModel getPropertyModel(String propName) {
        IBeanPropertyModel propModel = propertyModels.get(propName);
        if (propModel == null) {
            if (propAliases != null) {
                String alias = propAliases.get(propName);
                if (alias != null)
                    propModel = propertyModels.get(alias);
            }
        }
        return propModel;
    }

    @Override
    public IGenericType getBuildPropertyType(String propName) {
        if (this.builderMethodProvider != null) {
            IFunctionModel method = this.builderMethodProvider.apply(propName);
            if (method == null)
                return null;
            return method.getReturnType();
        }
        return null;
    }

    @Override
    public void buildProperty(Object obj, String propName, Object value) {
        Guard.notNull(this.builderMethodProvider, "null builderMethodProvider");
        IFunctionModel method = this.builderMethodProvider.apply(propName);
        if (method != null) {
            method.call1(obj, value, DisabledEvalScope.INSTANCE);
        }
    }

    @Override
    public Set<String> getExtPropertyNames(Object obj) {
        if (extPropertyGetter == null)
            return null;
        return extPropertyGetter.getExtPropNames(obj);
    }

    @Override
    public boolean isAllowExtProperty(Object obj, String propName) {
        if (extPropertyGetter == null)
            return false;
        return extPropertyGetter.isAllowExtProperty(obj, propName);
    }

    @Override
    public Object getExtProperty(Object obj, String propName, IEvalScope scope) {
        Guard.notNull(this.extPropertyGetter, "extPropertyGetter");
        return extPropertyGetter.getProperty(obj, propName, scope);
    }

    @Override
    public void setExtProperty(Object obj, String propName, Object value, IEvalScope scope) {
        Guard.notNull(this.extPropertySetter, "extPropertySetter");
        extPropertySetter.setProperty(obj, propName, value, scope);
    }

    @Override
    public IPropertyGetter getExtPropertyMaker() {
        return extPropertyMaker;
    }

    @Override
    public Object makeExtProperty(Object bean, String propName, IEvalScope scope) {
        Guard.notNull(this.extPropertyMaker, "extPropertyMaker");
        return extPropertyMaker.getProperty(bean, propName, scope);
    }

    @Override
    public int getSize(Object bean) {
        if (isArray())
            return Array.getLength(bean);
        if (isCollectionLike())
            return ((Collection) bean).size();
        return -1;
    }

    @Override
    public void forEach(Object bean, ObjIntConsumer<Object> action) {
        if (isCollectionLike()) {
            int index = 0;
            Collection c = (Collection) bean;
            for (Object o : c) {
                action.accept(o, index++);
            }
        } else if (isArray()) {
            for (int i = 0, n = Array.getLength(bean); i < n; i++) {
                action.accept(Array.get(bean, i), i);
            }
        }
    }

    @Override
    public Iterator<Object> iterator(Object bean) {
        if (isCollectionLike())
            return ((Collection) bean).iterator();
        if (isArray())
            return new ArrayIterator(bean);
        return Collections.singletonList(bean).iterator();
    }
}