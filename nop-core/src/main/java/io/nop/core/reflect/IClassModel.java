/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/**
 * 对反射模型的统一封装，不暴露java Class等底层对象，因此可以完全脱离java反射机制来实现.
 * <p>
 * 静态函数和普通成员函数作为不同的集合管理。
 */
public interface IClassModel extends IAnnotatedElement {
    default String getClassName() {
        return getRawClass().getName();
    }

    String getSimpleName();

    default Class getRawClass() {
        return getType().getRawClass();
    }

    IGenericType getType();

    default boolean isInterface() {
        return getType().isInterface();
    }

    default boolean isArray() {
        return getType().isArray();
    }

    default boolean isAbstract() {
        Class<?> rawClass = getRawClass();
        return Modifier.isAbstract(rawClass.getModifiers()) || rawClass.isInterface();
    }

    /**
     * 本类中声明的属性，不包括静态属性
     */
    Map<String, ? extends IFieldModel> getDeclaredFields();

    /**
     * 本类中声明的方法，不包括静态方法
     */
    List<? extends IFunctionModel> getDeclaredMethods();

    /**
     * 可以访问到的本类以及派生类中的所有属性
     */
    Map<String, ? extends IFieldModel> getFields();

    List<? extends IFunctionModel> getConstructors();

    default Object newInstance() {
        return getConstructor(0).call0(null, DisabledEvalScope.INSTANCE);
    }

    List<? extends IFunctionModel> getMethods();

    IFieldModel getField(String name);

    IFunctionModel getConstructor(int argCount);

    boolean hasConstructorWithArgCount(int argCount);

    IFunctionModel getConstructor(Class[] argTypes);

    IFunctionModel getConstructorForArgs(Object[] argValues);

    /**
     * 名字为指定名称的所有方法。包含本类以及派生类中定义的方法
     *
     * @param name 方法名称
     */
    IMethodModelCollection getMethodsByName(String name);

    default IFunctionModel getMethod(String name, int argCount) {
        IMethodModelCollection coll = getMethodsByName(name);
        if (coll == null)
            return null;
        return coll.getUniqueMethod(argCount);
    }

    IFunctionModel getMethodWithAnnotation(Class<? extends Annotation> annClass, int argCount);

    default IFunctionModel getStaticMethod(String name, int argCount) {
        IMethodModelCollection coll = getStaticMethodsByName(name);
        if (coll == null)
            return null;
        return coll.getUniqueMethod(argCount);
    }

    IFunctionModel getMethodByExactType(String name, Class<?>... argTypes);

    // =================== 以下为静态属性和方法 ======================
    Map<String, ? extends IFieldModel> getDeclaredStaticFields();

    List<? extends IFunctionModel> getDeclaredStaticMethods();

    Map<String, ? extends IFieldModel> getStaticFields();

    List<? extends IFunctionModel> getStaticMethods();

    IFunctionModel getFactoryMethod();

    IFieldModel getStaticField(String name);

    IMethodModelCollection getStaticMethodsByName(String name);

    IBeanModel getBeanModel();
}