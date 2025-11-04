/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.utils;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericArrayTypeImpl;
import io.nop.core.type.impl.GenericIntersectionTypeImpl;
import io.nop.core.type.impl.GenericTypeVariableBoundImpl;
import io.nop.core.type.impl.GenericWildcardTypeImpl;
import io.nop.core.type.impl.PredefinedGenericType;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_TYPE_UNSUPPORTED_JAVA_TYPE;

@Internal
public class JavaGenericTypeBuilder {
    public static List<IGenericType> buildTypeBounds(TypeVariable<?>[] vars, Set<String> existingVars) {
        if (vars.length == 0)
            return Collections.emptyList();

        List<IGenericType> ret = new ArrayList<>(vars.length);
        for (TypeVariable<?> var : vars) {
            ret.add(buildTypeBound(var, existingVars));
        }
        return ret;
    }

    public static IGenericType buildTypeBound(TypeVariable<?> var, Set<String> existingVars) {
        if (!existingVars.add(var.getName()))
            return GenericTypeHelper.buildTypeVariable(var.getName());

        Type[] bounds = var.getBounds();
        if (bounds.length == 0) {
            return GenericTypeHelper.buildTypeVariable(var.getName());
        }
        if (bounds.length == 1) {
            if (bounds[0] == Object.class)
                return GenericTypeHelper.buildTypeVariable(var.getName());
            IGenericType bound = buildGenericType(bounds[0], existingVars);
            return new GenericTypeVariableBoundImpl(var.getName(), bound, null);
        }
        IGenericType bound = buildIntersectionType(bounds, existingVars);
        return new GenericTypeVariableBoundImpl(var.getName(), bound, null);
    }

    public static IGenericType buildTypeVariable(TypeVariable<?> var) {
        return GenericTypeHelper.buildTypeVariable(var.getName());
    }

    public static IGenericType buildGenericType(Type type) {
        return buildGenericType(type, new HashSet<>());
    }

    public static IGenericType buildGenericType(Type type, Set<String> existingVars) {
        if (type instanceof Class<?>) {
            return buildRawType((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            return buildParameterizedType((ParameterizedType) type, existingVars);
        } else if (type instanceof TypeVariable<?>) {
            return buildTypeBound((TypeVariable<?>) type, existingVars);
        } else if (type instanceof WildcardType) {
            return buildWildcardType((WildcardType) type, existingVars);
        } else if (type instanceof GenericArrayType) {
            return buildArrayType((GenericArrayType) type, existingVars);
        } else {
            throw new NopException(ERR_TYPE_UNSUPPORTED_JAVA_TYPE).param(ARG_TYPE_NAME, type.getTypeName());
        }
    }

    public static IGenericType buildRawType(Class<?> clazz) {
        PredefinedGenericType type = PredefinedGenericTypes.getPredefinedType(clazz.getTypeName());
        if (type != null)
            return type;

        if (clazz.isArray()) {
            IGenericType componentType = buildRawType(clazz.getComponentType());
            return new GenericArrayTypeImpl(componentType);
        }

        return ReflectionManager.instance().newRawType(clazz);
    }

    public static IGenericType buildParameterizedType(ParameterizedType type, Set<String> existingVars) {
        Class<?> clazz = (Class<?>) type.getRawType();
        IGenericType rawType = buildRawType(clazz);
        List<IGenericType> params = buildGenericTypes(type.getActualTypeArguments(), existingVars);
        return GenericTypeHelper.buildParameterizedType(rawType, params);
    }

    public static IGenericType buildParameterizedType(Class<?> rawType, Class<?>... paramTypes) {
        IGenericType javaType = buildRawType(rawType);
        List<IGenericType> params = buildGenericTypes(paramTypes);
        return GenericTypeHelper.buildParameterizedType(javaType, params);
    }

    public static IGenericType buildListType(Type componentType) {
        IGenericType type = ReflectionManager.instance().buildGenericType(componentType);
        return GenericTypeHelper.buildParameterizedType(PredefinedGenericTypes.LIST_TYPE, Arrays.asList(type));
    }

    public static IGenericType buildMapType(Type componentType) {
        IGenericType type = ReflectionManager.instance().buildGenericType(componentType);
        return GenericTypeHelper.buildParameterizedType(PredefinedGenericTypes.MAP_TYPE,
                Arrays.asList(PredefinedGenericTypes.STRING_TYPE, type));
    }

    public static List<IGenericType> buildGenericTypes(Type[] types) {
        return buildGenericTypes(types, new HashSet<>());
    }

    public static List<IGenericType> buildGenericTypes(Type[] types, Set<String> existingVars) {
        if (types == null || types.length == 0)
            return Collections.emptyList();

        List<IGenericType> ret = new ArrayList<>(types.length);
        for (Type type : types) {
            ret.add(buildGenericType(type, existingVars));
        }
        return ret;
    }

    public static IGenericType buildArrayType(GenericArrayType type, Set<String> existingVars) {
        IGenericType baseType = buildGenericType(type.getGenericComponentType(), existingVars);
        if (baseType.isPredefined()) {
            IGenericType arrayType = PredefinedGenericTypes.getPredefinedArrayType((PredefinedGenericType) baseType);
            if (arrayType != null)
                return arrayType;
        }
        return new GenericArrayTypeImpl(baseType);
    }

    public static IGenericType buildIntersectionType(Type[] types, Set<String> existingVars) {
        if (types == null || types.length == 0)
            return PredefinedGenericTypes.ANY_TYPE;

        if (types.length == 1)
            return buildGenericType(types[0], existingVars);

        List<IGenericType> ret = new ArrayList<>(types.length);
        for (Type type : types) {
            ret.add(buildGenericType(type, existingVars));
        }
        return new GenericIntersectionTypeImpl(CollectionHelper.immutableList(ret));
    }

    public static IGenericType buildWildcardType(WildcardType type, Set<String> existingVars) {
        IGenericType upperBound = buildIntersectionType(type.getUpperBounds(), existingVars);
        IGenericType lowerBound = buildLowerBounds(type.getLowerBounds(), existingVars);
        return GenericWildcardTypeImpl.valueOf(upperBound, lowerBound);
    }

    public static IGenericType buildLowerBounds(Type[] bounds, Set<String> existingVars) {
        if (bounds == null || bounds.length == 0)
            return null;
        return buildGenericType(bounds[0], existingVars);
    }
}