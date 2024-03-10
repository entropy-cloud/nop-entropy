/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.core.type.utils.GenericTypeHelper;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_FUNCTION_TYPE;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_MAP_TYPE;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_STRUCTURE_TYPE;

/**
 * 对应于程序语言中泛型声明。在Java类型系统上做了扩展，包含UnionType, IntersectionType, FunctionType, TupleType等。
 * <p>
 * 泛型声明必须是Immutable的
 */
@ImmutableBean
public interface IGenericType extends Serializable, Type , IJsonString {

    /**
     * 对于泛型声明，包含所有泛型参数信息。对于StdDataType中包含的类型，因为系统中使用非常频繁，这里去除了包名部分以减少输出长度。 例如java.lang.String对应的typeName是String。
     */
    String getTypeName();

    default boolean isJavaDefaultImportType() {
        if (!isResolved())
            return false;
        return StringHelper.isJavaDefaultImportType(getRawTypeName());
    }

    /**
     * 不包含泛型参数的类型名
     */
    String getRawTypeName();

    /**
     * 标准数据类型根据名称即可直接确定
     */
    StdDataType getStdDataType();

    String toString();

    GenericTypeKind getKind();

    default boolean hasTypeParameter() {
        return !getTypeParameters().isEmpty();
    }

    default IGenericType getRawType() {
        return this;
    }

    /**
     * 返回泛型参数
     */
    default List<IGenericType> getTypeParameters() {
        return Collections.emptyList();
    }

    /**
     * 是否包含泛型参数
     */
    default boolean containsTypeVariable() {
        List<IGenericType> params = getTypeParameters();
        if (params.isEmpty())
            return false;
        for (IGenericType type : params) {
            if (type.containsTypeVariable())
                return true;
        }
        return false;
    }

    /**
     * 是否是在PredefinedGenericTypes常量类中定义的类型
     */
    default boolean isPredefined() {
        return false;
    }

    /**
     * 是否java primitive类型，如int, double等
     */
    default boolean isPrimitive() {
        return false;
    }

    default boolean isBooleanType() {
        return this == PredefinedGenericTypes.PRIMITIVE_BOOLEAN_TYPE || this == PredefinedGenericTypes.BOOLEAN_TYPE;
    }

    default boolean isVoidType() {
        return this == PredefinedGenericTypes.VOID_TYPE || this == PredefinedGenericTypes.PRIMITIVE_VOID_TYPE;
    }

    /**
     * 是否Java数组类型。数组为定长结构，不能动态改变长度
     */
    default boolean isArray() {
        return getKind() == GenericTypeKind.ARRAY;
    }

    default boolean isContainerType() {
        return isCollectionLike() || isMapLike();
    }

    /**
     * 是否实现了java.util.List接口
     */
    default boolean isListLike() {
        return false;
    }

    /**
     * 是否实现了java.util.Collection接口
     */
    default boolean isCollectionLike() {
        return isListLike() || isSetLike();
    }

    /**
     * 是否实现了java.util.Set接口
     */
    default boolean isSetLike() {
        return false;
    }

    /**
     * 是否实现了java.util.Map接口
     */
    default boolean isMapLike() {
        return getStdDataType() == StdDataType.MAP;
    }

    default boolean isTuple() {
        return getKind() == GenericTypeKind.TUPLE;
    }

    default boolean isUnion() {
        return getKind() == GenericTypeKind.UNION;
    }

    default boolean isIntersection() {
        return getKind() == GenericTypeKind.INTERSECTION;
    }

    default boolean isTypeVariable() {
        return getKind().isVariable();
    }

    default boolean isTypeVariableBound() {
        return getKind() == GenericTypeKind.TYPE_VARIABLE_BOUND;
    }

    default boolean isWildcard() {
        return getKind() == GenericTypeKind.WILDCARD;
    }

    default boolean isFunctionalClass() {
        return false;
    }

    default boolean isFunction() {
        return getKind() == GenericTypeKind.FUNCTION;
    }

    default boolean isDataBean() {
        return ClassHelper.isDataBean(getRawClass());
    }

    default boolean isAnyType() {
        return this == PredefinedGenericTypes.ANY_TYPE;
    }

    /**
     * 1. 如果是Collection类型，则这里返回集合元素的泛型类型 2. 如果是Array类型，则这里返回数组元素的泛型类型
     */
    default IGenericType getComponentType() {
        throw new NopException(ERR_TYPE_NOT_ARRAY_OR_LIST_TYPE).param(ARG_TYPE_NAME, getTypeName());
    }

    default IGenericType getMapKeyType() {
        throw new NopException(ERR_TYPE_NOT_MAP_TYPE).param(ARG_TYPE_NAME, getTypeName());
    }

    /**
     * 如果是Map类型，则这里返回value的泛型类型
     *
     * @return 如果不是Map类型，则返回null
     */
    default IGenericType getMapValueType() {
        throw new NopException(ERR_TYPE_NOT_MAP_TYPE).param(ARG_TYPE_NAME, getTypeName());
    }

    /**
     * 对于Tuple/Union/Intersection类型，返回子类型列表
     */
    default List<IGenericType> getSubTypes() {
        throw new NopException(ERR_TYPE_NOT_STRUCTURE_TYPE).param(ARG_TYPE_NAME, getTypeName());
    }

    /**
     * 将类型变量替换为具体类型
     */
    default IGenericType refine(ITypeScope resolver) {
        return this;
    }

    default void resolveClassName(Function<String, String> resolver) {
        resolve(typeName -> {
            String resolvedName = resolver.apply(typeName);
            if (resolvedName == null || Objects.equals(resolvedName, typeName)) {
                return null;
            }
            return new GenericRawTypeReferenceImpl(resolvedName);
        });
    }

    /**
     * 当前类型如果含有泛型变量，则从contextType中查找对应泛型变量的具体值，并替换本类中的泛型变量。
     *
     * @param rawContextType 定义了泛型变量的类，例如Map<K,V>
     * @param contextType    填充了泛型变量的类，例如Map<String,Object>
     * @return 替换了泛型变量后得到的具体类型
     */
    default IGenericType refine(IGenericType rawContextType, IGenericType contextType) {
        if (!getKind().isVariable() && !this.hasTypeParameter())
            return this;
        return GenericTypeHelper.refineType(this, rawContextType.getTypeParameters(), contextType.getTypeParameters());
    }

    /**
     * 查找superType和interfaces，返回与clazz匹配的泛型类型。 例如对于 {@code class A extends List<String>}，
     * getGenericType(List.class)将返回{@code List<String>}
     */
    default IGenericType getGenericType(Class<?> clazz) {
        return getGenericType(PredefinedGenericTypes.normalizeTypeName(clazz));
    }

    default IGenericType getGenericType(String rawTypeName) {
        return null;
    }

    /**
     * 是否已经和Java class关联
     */
    default boolean isResolved() {
        return true;
    }

    /**
     * 解析所有RawTypeReference
     */
    void resolve(IRawTypeResolver resolver);

    // ==================== 以下方法仅当resolved=true时方才有效 ===============

    /**
     * <p>
     * 对于TypeVariable返回getResolvedBound().getResolvedType()
     * <p>
     * 对于TypeVariableBound返回UpperBound
     * <p>
     * 对RawTypeReference返回对应的rawType
     */
    default IGenericType getResolvedType() {
        return this;
    }

    /**
     * 当resolved=true的时候，返回对应的java类型，否则返回Object.class
     */
    Class<?> getRawClass();

    default IGenericType getSuperType() {
        return null;
    }

    /**
     * 返回本类型所直接实现的接口，不包含父类中的接口以及接口所继承的接口
     */
    default List<IGenericType> getInterfaces() {
        return Collections.emptyList();
    }

    default boolean isAssignableFrom(Class clazz) {
        return getRawClass().isAssignableFrom(clazz);
    }

    default boolean isAssignableTo(Class clazz) {
        return clazz.isAssignableFrom(getRawClass());
    }

    default boolean isAssignableFrom(IGenericType type) {
        if (type.getKind() == GenericTypeKind.CLASS_TYPE)
            return type.isAssignableTo(this);
        return isAssignableFrom(type.getRawClass());
    }

    default boolean isAssignableTo(IGenericType type) {
        return isAssignableTo(type.getRawClass());
    }

    default boolean isTypeOrSubTypeOf(Class<?> clazz) {
        Class clz = getRawClass();
        return (clazz == clz) || clazz.isAssignableFrom(clz);
    }

    default boolean isTypeOrSuperTypeOf(Class<?> clazz) {
        Class clz = getRawClass();
        return (clazz == clz) || clz.isAssignableFrom(clazz);
    }

    default boolean isInstance(Object obj) {
        return getRawClass().isInstance(obj);
    }

    default String getPackageName() {
        return StringHelper.packageName(getClassName());
    }

    default String getClassName() {
        return getRawClass().getName();
    }

    default String getSimpleClassName() {
        return StringHelper.simpleClassName(getClassName());
    }

    default boolean isInterface() {
        return getRawClass().isInterface();
    }

    default boolean isAnnotation() {
        return getRawClass().isAnnotation();
    }

    default boolean isEnum() {
        return getRawClass().isEnum();
    }

    default boolean isAbstract() {
        return Modifier.isAbstract(getRawClass().getModifiers());
    }

    default List<IGenericType> getFuncArgTypes() {
        throw new NopException(ERR_TYPE_NOT_FUNCTION_TYPE).param(ARG_TYPE_NAME, getTypeName());
    }

    default IGenericType getFuncReturnType() {
        throw new NopException(ERR_TYPE_NOT_FUNCTION_TYPE).param(ARG_TYPE_NAME, getTypeName());
    }
}