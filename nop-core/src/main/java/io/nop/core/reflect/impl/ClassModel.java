/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.util.FreezeHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ClassModel extends AnnotatedElement implements IClassModel {
    private static final long serialVersionUID = -4817536180258284698L;

    private final IGenericType type;

    private String className;
    private String simpleName;

    private IFunctionModel factoryMethod;

    private List<? extends IFunctionModel> methods = Collections.emptyList();
    private Map<String, MethodModelCollection> methodCollections = Collections.emptyMap();
    private MethodModelCollection constructors = MethodModelCollection.EMPTY;
    private Map<String, ? extends IFieldModel> fields = Collections.emptyMap();
    private Map<String, FieldModel> declaredFields = Collections.emptyMap();
    private List<IFunctionModel> declaredMethods = Collections.emptyList();

    private Map<String, FieldModel> declaredStaticFields = Collections.emptyMap();
    private List<IFunctionModel> declaredStaticMethods = Collections.emptyList();

    private List<? extends IFunctionModel> staticMethods = Collections.emptyList();
    private Map<String, ? extends IFieldModel> staticFields = Collections.emptyMap();
    private Map<String, MethodModelCollection> staticMethodCollections = Collections.emptyMap();

    private IBeanModel beanModel;

    private boolean immutable;

    public ClassModel(IGenericType type) {
        this.type = type;
        setClassName(type.getClassName());
    }

    @Override
    public void freeze(boolean cascade) {
        super.freeze(cascade);
        constructors.freeze(true);

        FreezeHelper.freezeItems(methodCollections.values(), true);
        FreezeHelper.freezeItems(staticMethodCollections.values(), true);
        FreezeHelper.freezeItems(declaredFields.values(), true);
        FreezeHelper.freezeItems(declaredStaticFields.values(), true);
    }

    public String toString() {
        return className;
    }

    public IGenericType getType() {
        return type;
    }

    @Override
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        checkReadonly();
        this.className = className;
        this.simpleName = StringHelper.lastPart(className, '.');
    }

    @Override
    public Map<String, FieldModel> getDeclaredFields() {
        return declaredFields;
    }

    public void setDeclaredFields(Map<String, FieldModel> declaredFields) {
        checkReadonly();
        this.declaredFields = declaredFields;
    }

    @Override
    public List<IFunctionModel> getDeclaredMethods() {
        return declaredMethods;
    }

    public void setDeclaredMethods(List<IFunctionModel> declaredMethods) {
        checkReadonly();
        this.declaredMethods = declaredMethods;
    }

    @Override
    public List<IFunctionModel> getConstructors() {
        return constructors.getMethods();
    }

    public void setConstructors(MethodModelCollection constructors) {
        checkReadonly();
        this.constructors = constructors;
    }

    @Override
    public IFunctionModel getConstructorForArgs(Object[] argValues) {
        return constructors.getMethodForArgValues(argValues);
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        checkReadonly();
        this.immutable = immutable;
    }

    @Override
    public Map<String, ? extends IFieldModel> getFields() {
        return fields;
    }

    @Override
    public String getSimpleName() {
        return simpleName;
    }

    @Override
    public List<? extends IFunctionModel> getMethods() {
        return methods;
    }

    @Override
    public IFieldModel getField(String name) {
        return fields.get(name);
    }

    @Override
    public IFunctionModel getConstructor(int argCount) {
        return constructors.getUniqueMethod(argCount);
    }

    public boolean hasConstructorWithArgCount(int argCount) {
        return constructors.hasMethodWithArgCount(argCount);
    }

    @Override
    public IFunctionModel getConstructor(Class[] argTypes) {
        return constructors.getMethodForArgTypes(argTypes);
    }

    @Override
    public IMethodModelCollection getMethodsByName(String name) {
        return methodCollections.get(name);
    }

    public IFunctionModel getMethodByExactType(String name, Class<?>... argTypes) {
        IMethodModelCollection coll = getMethodsByName(name);
        if (coll == null)
            return null;
        return coll.getExactMatchMethod(argTypes);
    }

    @Override
    public IFunctionModel getMethodWithAnnotation(Class<? extends Annotation> annClass, int argCount) {
        for (IMethodModelCollection array : this.methodCollections.values()) {
            for (IFunctionModel method : array.getMethods()) {
                if (method.getArgCount() == argCount || (method.isVarArgs() && method.getArgCount() <= argCount)) {
                    if (method.isAnnotationPresent(annClass))
                        return method;
                }
            }
        }
        return null;
    }

    @Override
    public Map<String, ? extends IFieldModel> getDeclaredStaticFields() {
        return declaredStaticFields;
    }

    @Override
    public List<? extends IFunctionModel> getDeclaredStaticMethods() {
        return declaredStaticMethods;
    }

    @Override
    public Map<String, ? extends IFieldModel> getStaticFields() {
        return staticFields;
    }

    public void setMethods(List<? extends IFunctionModel> methods) {
        checkReadonly();
        this.methods = methods;
    }

    public void setFields(Map<String, ? extends IFieldModel> fields) {
        checkReadonly();
        this.fields = fields;
    }

    public void setDeclaredStaticFields(Map<String, FieldModel> declaredStaticFields) {
        checkReadonly();
        this.declaredStaticFields = declaredStaticFields;
    }

    public void setDeclaredStaticMethods(List<IFunctionModel> declaredStaticMethods) {
        checkReadonly();
        this.declaredStaticMethods = declaredStaticMethods;
    }

    public void setStaticFields(Map<String, ? extends IFieldModel> staticFields) {
        checkReadonly();
        this.staticFields = staticFields;
    }

    public void setStaticMethodCollections(Map<String, MethodModelCollection> staticMethodCollections) {
        checkReadonly();
        this.staticMethodCollections = staticMethodCollections;
    }

    public void setMethodCollections(Map<String, MethodModelCollection> methodCollections) {
        checkReadonly();
        this.methodCollections = methodCollections;
    }

    @Override
    public IFieldModel getStaticField(String name) {
        return staticFields.get(name);
    }

    @Override
    public IMethodModelCollection getStaticMethodsByName(String name) {
        return staticMethodCollections.get(name);
    }

    @Override
    public List<? extends IFunctionModel> getStaticMethods() {
        return staticMethods;
    }

    public void setStaticMethods(List<? extends IFunctionModel> methods) {
        checkReadonly();
        staticMethods = methods;
    }

    @Override
    public IFunctionModel getFactoryMethod() {
        return factoryMethod;
    }

    public void setFactoryMethod(IFunctionModel factoryMethod) {
        checkReadonly();
        this.factoryMethod = factoryMethod;
    }

    @Override
    public IBeanModel getBeanModel() {
        IBeanModel beanModel = this.beanModel;
        if (beanModel != null)
            return beanModel;

        synchronized (this) {
            beanModel = this.beanModel;
            if (beanModel != null)
                return beanModel;
            if (Annotation.class.isAssignableFrom(getRawClass())) {
                beanModel = this.beanModel = new AnnotationBeanModelBuilder().buildFromClassModel(this);
            } else {
                beanModel = this.beanModel = new BeanModelBuilder().buildFromClassModel(this);
            }
        }

        return beanModel;
    }
}