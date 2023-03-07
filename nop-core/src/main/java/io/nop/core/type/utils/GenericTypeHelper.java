/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type.utils;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawType;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.ITypeVariable;
import io.nop.core.type.IWildcardType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericArrayTypeImpl;
import io.nop.core.type.impl.GenericParameterizedType;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.core.type.impl.GenericTypeVariableBoundImpl;
import io.nop.core.type.impl.GenericTypeVariableImpl;
import io.nop.core.type.impl.GenericWildcardTypeImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GenericTypeHelper {
    public static String buildParameterizedTypeName(IGenericType rawType, List<IGenericType> typeParameters) {
        StringBuilder sb = new StringBuilder();
        sb.append(rawType.getRawTypeName());
        sb.append('<');
        for (int i = 0, n = typeParameters.size(); i < n; i++) {
            sb.append(typeParameters.get(i));
            if (i != n - 1) {
                sb.append(',');
            }
        }
        sb.append('>');
        return sb.toString();
    }

    public static IGenericType buildArrayType(IGenericType itemType) {
        IGenericType type = new GenericArrayTypeImpl(itemType);
        if (itemType.isPredefined()) {
            type = PredefinedGenericTypes.normalizeType(type);
        }
        return type;
    }

    public static IGenericType buildRawType(String className) {
        IGenericType type = PredefinedGenericTypes.getPredefinedType(className);
        if (type == null)
            type = new GenericRawTypeReferenceImpl(className);
        return type;
    }

    /**
     * @param itemType 数组元素的类型
     * @param brackets []的个数，对应于几重数组
     * @return 返回数组类型
     */
    public static IGenericType buildArrayType(IGenericType itemType, int brackets) {
        if (brackets <= 0)
            return itemType;
        IGenericType type = itemType;
        for (int i = 0; i < brackets; i++) {
            type = buildArrayType(type);
        }
        return type;
    }

    public static IGenericType buildListType(IGenericType itemType) {
        return buildParameterizedType(PredefinedGenericTypes.LIST_TYPE, Collections.singletonList(itemType));
    }

    public static IGenericType buildSetType(IGenericType itemType) {
        return buildParameterizedType(PredefinedGenericTypes.SET_TYPE, Collections.singletonList(itemType));
    }

    public static IGenericType buildMapType(IGenericType itemType) {
        return buildParameterizedType(PredefinedGenericTypes.MAP_TYPE,
                Arrays.asList(PredefinedGenericTypes.STRING_TYPE, itemType));
    }

    public static IGenericType buildParameterizedType(IGenericType rawType, List<IGenericType> params) {
        if (params.isEmpty())
            return rawType;

        IGenericType type = new GenericParameterizedType(rawType, params);
        if (rawType.isPredefined()) {
            type = PredefinedGenericTypes.normalizeType(type);
        }
        return type;
    }

    public static IGenericType buildRequestType(IGenericType type) {
        return buildParameterizedType(PredefinedGenericTypes.API_REQUEST_TYPE, Collections.singletonList(type));
    }

    public static IGenericType buildTypeVariable(String name) {
        IGenericType type = PredefinedGenericTypes.getPredefinedVar(name);
        if (type != null)
            return type;
        return new GenericTypeVariableImpl(name);
    }

    public static IGenericType buildTypeVariableBound(String name, IGenericType upperBound, IGenericType lowerBound) {
        return new GenericTypeVariableBoundImpl(name, upperBound, lowerBound);
    }

    public static IWildcardType buildWildcardType(IGenericType upperBound, IGenericType lowerBound) {
        return GenericWildcardTypeImpl.valueOf(upperBound, lowerBound);
    }

    public static boolean containsTypeVariable(List<IGenericType> types) {
        for (IGenericType type : types) {
            if (type.containsTypeVariable())
                return true;
        }
        return false;
    }

    /**
     * 对于ParameterizedType, 如果用于替换的类型与泛型参数相同，则refine操作不会改变原有类型，因此可以被忽略
     */
    public static boolean isRefineIgnorable(List<IGenericType> rawTypeParams, List<IGenericType> typeParams) {
        for (int i = 0, n = rawTypeParams.size(); i < n; i++) {
            if (!isSameTypeVariable(rawTypeParams.get(i), typeParams.get(i)))
                return false;
        }
        return true;
    }

    static boolean isSameTypeVariable(IGenericType typeA, IGenericType typeB) {
        if (typeA.isTypeVariable() && typeB.isTypeVariable()) {
            return ((ITypeVariable) typeA).getName().equals(((ITypeVariable) typeB).getName());
        }
        return false;
    }

    /**
     * 如果type中包含泛型变量，则它可以被替换为具体类型。例如 type = List<E>, typeParams = TypeVariable("E"), replacedTypeVars= String, 得到结果
     * List<String>
     *
     * @param type             待处理的类型
     * @param typeParams       包含type中用的到泛型参数的定义
     * @param replacedTypeVars typeParams中的泛型参数将被替换为指定的类型
     * @return 泛型参数被替换后的结果
     */
    public static IGenericType refineType(IGenericType type, List<IGenericType> typeParams,
                                          List<IGenericType> replacedTypeVars) {
        if (type == null)
            return null;

        if (type.getKind().isVariable()) {
            if (isRefineIgnorable(typeParams, replacedTypeVars))
                return type;

            ITypeVariable var = (ITypeVariable) type;
            ITypeScope scope = buildRefineScope(typeParams, replacedTypeVars);
            IGenericType resolved = scope.resolveVariable(var.getName());
            return resolved == null ? type : resolved;
        }

        List<IGenericType> params = GenericTypeHelper.replaceTypeVariable(type, typeParams, replacedTypeVars);
        if (params == null)
            return type;

        return PredefinedGenericTypes.normalizeType(new GenericParameterizedType(type.getRawType(), params));
    }

    public static IGenericType refineType(IGenericType type, IGenericType contextType) {
        IGenericType rawType = contextType.getRawType();
        return refineType(type, rawType.getTypeParameters(), contextType.getTypeParameters());
    }

    public static List<IGenericType> replaceTypeVariable(IGenericType type, List<IGenericType> typeBounds,
                                                         List<IGenericType> typeParams) {
        List<IGenericType> params = type.getTypeParameters();
        if (!containsVariable(params))
            return null;

        if (isRefineIgnorable(typeBounds, typeParams))
            return null;

        ITypeScope scope = buildRefineScope(typeBounds, typeParams);
        return refineTypes(params, scope);
    }

    public static boolean containsVariable(List<IGenericType> types) {
        if (types.isEmpty())
            return false;

        for (IGenericType type : types) {
            if (type.containsTypeVariable())
                return true;
        }

        return false;
    }

    public static DefaultTypeScope buildRefineScope(List<IGenericType> bounds, List<IGenericType> targetTypes) {
        DefaultTypeScope scope = new DefaultTypeScope();
        for (int i = 0, n = bounds.size(); i < n; i++) {
            IGenericType bound = bounds.get(i);
            IGenericType target = targetTypes.get(i);
            if (isSameTypeVariable(bound, target))
                continue;

            if (bound.isTypeVariable()) {
                String name = ((ITypeVariable) bound).getName();
                scope.addVariable(name, target);
            }
        }
        return scope;
    }

    public static List<IGenericType> refineTypes(List<IGenericType> list, ITypeScope resolver) {
        if (list.isEmpty())
            return list;

        List<IGenericType> ret = list;
        for (int i = 0, n = list.size(); i < n; i++) {
            IGenericType item = list.get(i);
            IGenericType refined = item.refine(resolver);
            if (refined != item) {
                if (ret == list) {
                    ret = new ArrayList<>(list);
                }
                ret.set(i, refined);
            }
        }
        if (ret != list)
            ret = CollectionHelper.immutableList(ret);
        return ret;
    }

    public static List<IGenericType> refineTypes(List<IGenericType> types, List<IGenericType> typeParams,
                                                 List<IGenericType> replacedTypeVars) {
        if (!containsVariable(types))
            return types;
        return refineTypes(types, buildRefineScope(typeParams, replacedTypeVars));
    }

    /**
     * IRawType.getGenericType(clazz)的具体实现
     *
     * @param rawType 包含rawClass的泛型类型
     * @param clazz   待查找的java类
     * @return clazz对应的泛型对象。例如List<String>查找Collection.class，返回Collection<String>
     */
    public static IGenericType findGenericType(IRawType rawType, Class<?> clazz) {
        Class<?> rawClass = rawType.getRawClass();
        if (rawClass == clazz)
            return rawType;

        Class<?> superClass = rawClass.getSuperclass();
        if (superClass == clazz)
            return rawType.getSuperType();

        if (clazz.isInterface()) {
            Class<?>[] ifs = rawClass.getInterfaces();
            for (int i = 0, n = ifs.length; i < n; i++) {
                if (ifs[i] == clazz)
                    return rawType.getInterfaces().get(i);
                if (clazz.isAssignableFrom(ifs[i]))
                    return rawType.getInterfaces().get(i).getGenericType(clazz);
            }
        }
        if (superClass == null)
            return null;
        return rawType.getSuperType().getGenericType(clazz);
    }

    public static IGenericType findGenericType(IGenericType type, String rawTypeName) {
        if (type.getRawTypeName().equals(rawTypeName))
            return type;

        IGenericType superType = type.getSuperType();
        if (superType != null) {
            if (superType.getRawTypeName().equals(rawTypeName))
                return superType;
        }

        for (IGenericType infType : type.getInterfaces()) {
            IGenericType resolved = infType.getGenericType(rawTypeName);
            if (resolved != null)
                return resolved;
        }

        if (superType == null)
            return null;
        return findGenericType(superType, rawTypeName);
    }
}
