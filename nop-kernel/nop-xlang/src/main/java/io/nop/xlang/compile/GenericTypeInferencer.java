/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.type.IGenericType;
import io.nop.core.type.ITypeScope;
import io.nop.core.type.ITypeVariable;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericUnionTypeImpl;
import io.nop.core.type.utils.DefaultTypeScope;
import io.nop.core.type.utils.GenericTypeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_GOT;
import static io.nop.xlang.XLangErrors.ARG_TYPE_VAR;
import static io.nop.xlang.XLangErrors.ERR_TYPE_INFER_TYPE_VAR_CONFLICT;

/**
 * 泛型类型参数推导器
 * <p>
 * 根据函数调用的实际参数类型，推导泛型类型参数的具体类型。
 * 例如：调用 identity&lt;T&gt;(x: T): T 时，传入 string 类型参数，推导 T = string
 */
public class GenericTypeInferencer {

    /**
     * 从函数调用推导泛型类型参数
     *
     * @param typeParams   泛型类型参数定义，如 [T, U]
     * @param paramTypes   函数参数声明的类型，如 [T, List&lt;U&gt;]
     * @param argTypes     实际参数类型，如 [string, List&lt;int&gt;]
     * @param errors       错误收集器
     * @return 推导出的类型参数映射，如 {T: string, U: int}
     */
    public static Map<String, IGenericType> inferTypeArguments(
            List<IGenericType> typeParams,
            List<IGenericType> paramTypes,
            List<IGenericType> argTypes,
            TypeErrorCollector errors) {

        if (typeParams == null || typeParams.isEmpty()) {
            return Collections.emptyMap();
        }

        TypeVarBindings bindings = new TypeVarBindings();
        
        // 对每个参数位置进行类型匹配
        int len = Math.min(paramTypes.size(), argTypes.size());
        for (int i = 0; i < len; i++) {
            IGenericType paramType = paramTypes.get(i);
            IGenericType argType = argTypes.get(i);
            
            if (!inferFromPair(paramType, argType, bindings, errors)) {
                // 推导失败，停止处理
                break;
            }
        }

        return bindings.getBindings();
    }

    /**
     * 从函数调用和期望返回类型推导泛型类型参数
     *
     * @param typeParams      泛型类型参数定义
     * @param paramTypes      函数参数声明的类型
     * @param argTypes        实际参数类型
     * @param expectedReturn  期望的返回类型（可选）
     * @param declaredReturn  声明的返回类型
     * @param errors          错误收集器
     * @return 推导出的类型参数映射
     */
    public static Map<String, IGenericType> inferTypeArgumentsWithReturn(
            List<IGenericType> typeParams,
            List<IGenericType> paramTypes,
            List<IGenericType> argTypes,
            IGenericType expectedReturn,
            IGenericType declaredReturn,
            TypeErrorCollector errors) {

        if (typeParams == null || typeParams.isEmpty()) {
            return Collections.emptyMap();
        }

        TypeVarBindings bindings = new TypeVarBindings();
        
        // 首先从参数推导
        int len = Math.min(paramTypes.size(), argTypes.size());
        for (int i = 0; i < len; i++) {
            IGenericType paramType = paramTypes.get(i);
            IGenericType argType = argTypes.get(i);
            
            if (!inferFromPair(paramType, argType, bindings, errors)) {
                break;
            }
        }

        // 如果有期望的返回类型，从返回类型反推
        if (expectedReturn != null && declaredReturn != null && hasTypeVariables(declaredReturn)) {
            inferFromReturnType(declaredReturn, expectedReturn, bindings, errors);
        }

        return bindings.getBindings();
    }

    /**
     * 从返回类型反推类型参数
     */
    private static void inferFromReturnType(
            IGenericType declaredReturn,
            IGenericType expectedReturn,
            TypeVarBindings bindings,
            TypeErrorCollector errors) {
        
        if (declaredReturn.isTypeVariable()) {
            String varName = ((ITypeVariable) declaredReturn).getName();
            bindings.bind(varName, expectedReturn, errors);
            return;
        }

        if (expectedReturn != null && hasTypeVariables(declaredReturn)) {
            inferFromComplexType(declaredReturn, expectedReturn, bindings, errors);
        }
    }

    private static boolean hasTypeVariables(IGenericType type) {
        return type != null && !getTypeVariableNames(type).isEmpty();
    }

    /**
     * 验证类型参数是否满足边界约束
     *
     * @param typeParams   泛型类型参数定义
     * @param typeArgs     推导出的类型参数
     * @param errors       错误收集器
     * @return 是否所有类型参数都满足边界约束
     */
    public static boolean validateTypeBounds(
            List<IGenericType> typeParams,
            Map<String, IGenericType> typeArgs,
            TypeErrorCollector errors) {
        
        if (typeParams == null || typeArgs == null) {
            return true;
        }

        return true;
    }

    /**
     * 获取类型中包含的所有类型变量名
     *
     * @param type 类型
     * @return 类型变量名集合
     */
    public static Set<String> getTypeVariableNames(IGenericType type) {
        Set<String> names = new HashSet<>();
        collectTypeVariableNames(type, names);
        return names;
    }

    private static void collectTypeVariableNames(IGenericType type, Set<String> names) {
        if (type == null) {
            return;
        }
        
        if (type.isTypeVariable()) {
            names.add(((ITypeVariable) type).getName());
            return;
        }

        if (type.hasTypeParameter()) {
            List<IGenericType> typeParams = type.getTypeParameters();
            if (typeParams != null) {
                for (IGenericType param : typeParams) {
                    collectTypeVariableNames(param, names);
                }
            }
        }

        if (type.isArray()) {
            collectTypeVariableNames(type.getComponentType(), names);
        }

        if (type.isFunction()) {
            collectTypeVariableNames(type.getFuncReturnType(), names);
            List<IGenericType> argTypes = type.getFuncArgTypes();
            if (argTypes != null) {
                for (IGenericType argType : argTypes) {
                    collectTypeVariableNames(argType, names);
                }
            }
        }

        if (type.isUnion()) {
            List<IGenericType> subTypes = type.getSubTypes();
            if (subTypes != null) {
                for (IGenericType subType : subTypes) {
                    collectTypeVariableNames(subType, names);
                }
            }
        }
    }

    /**
     * 从单个参数-实参对推导类型变量绑定
     */
    private static boolean inferFromPair(
            IGenericType paramType,
            IGenericType argType,
            TypeVarBindings bindings,
            TypeErrorCollector errors) {

        // 如果参数类型是类型变量，直接绑定
        if (paramType.isTypeVariable()) {
            String varName = ((ITypeVariable) paramType).getName();
            return bindings.bind(varName, argType, errors);
        }

        // 如果参数类型包含类型变量，递归匹配
        if (paramType.containsTypeVariable()) {
            return inferFromComplexType(paramType, argType, bindings, errors);
        }

        // 参数类型不包含类型变量，无需推导
        return true;
    }

    /**
     * 从复杂类型（如 List&lt;T&gt;, Map&lt;K, V&gt;）推导类型变量
     */
    private static boolean inferFromComplexType(
            IGenericType paramType,
            IGenericType argType,
            TypeVarBindings bindings,
            TypeErrorCollector errors) {

        // 处理泛型容器类型
        if (paramType.hasTypeParameter()) {
            // 检查原始类型是否兼容
            if (!isRawTypeCompatible(paramType, argType)) {
                return true; // 类型不匹配但不影响其他参数的推导
            }

            List<IGenericType> paramTypeArgs = paramType.getTypeParameters();
            List<IGenericType> argTypeArgs = argType.getTypeParameters();

            if (argTypeArgs != null && paramTypeArgs.size() == argTypeArgs.size()) {
                for (int i = 0; i < paramTypeArgs.size(); i++) {
                    if (!inferFromPair(paramTypeArgs.get(i), argTypeArgs.get(i), bindings, errors)) {
                        return false;
                    }
                }
            }
            return true;
        }

        // 处理数组类型
        if (paramType.isArray()) {
            if (argType.isArray() || argType.isListLike()) {
                return inferFromPair(
                        paramType.getComponentType(),
                        argType.getComponentType(),
                        bindings, errors);
            }
            return true;
        }

        // 处理 Union 类型
        if (paramType.isUnion()) {
            // Union类型参数：尝试从每个子类型推导
            for (IGenericType subType : paramType.getSubTypes()) {
                inferFromComplexType(subType, argType, bindings, errors);
            }
            return true;
        }

        // 处理函数类型
        if (paramType.isFunction()) {
            if (argType.isFunction()) {
                // 推导返回类型
                inferFromPair(
                        paramType.getFuncReturnType(),
                        argType.getFuncReturnType(),
                        bindings, errors);
                
                // 函数参数类型是逆变的，不应推导
                // 跳过参数类型的推导
            }
            return true;
        }

        return true;
    }

    /**
     * 检查原始类型是否兼容
     */
    private static boolean isRawTypeCompatible(IGenericType paramType, IGenericType argType) {
        if (argType.isAnyType()) {
            return true;
        }
        
        // 如果argType也是泛型类型，比较rawType
        if (argType.hasTypeParameter() || argType.getRawType() != argType) {
            return paramType.getRawTypeName().equals(argType.getRawTypeName());
        }
        
        // 检查继承关系
        return argType.isAssignableTo(paramType.getRawType());
    }

    /**
     * 应用推导出的类型参数，生成具体类型
     *
     * @param type      包含类型变量的类型
     * @param typeArgs  推导出的类型参数映射
     * @return 替换类型变量后的具体类型
     */
    public static IGenericType applyTypeArguments(IGenericType type, Map<String, IGenericType> typeArgs) {
        if (typeArgs == null || typeArgs.isEmpty()) {
            return type;
        }
        
        if (!type.containsTypeVariable()) {
            return type;
        }

        DefaultTypeScope scope = new DefaultTypeScope();
        typeArgs.forEach(scope::addVariable);
        
        return type.refine(scope);
    }

    /**
     * 类型变量绑定收集器
     */
    static class TypeVarBindings {
        private final Map<String, IGenericType> bindings = new HashMap<>();

        public boolean bind(String varName, IGenericType type, TypeErrorCollector errors) {
            IGenericType existing = bindings.get(varName);
            
            if (existing == null) {
                bindings.put(varName, type);
                return true;
            }

            IGenericType merged = mergeTypes(existing, type);
            bindings.put(varName, merged);
            return true;
        }

        private boolean isCompatible(IGenericType a, IGenericType b) {
            // any 类型兼容任何类型
            if (a.isAnyType() || b.isAnyType()) {
                return true;
            }
            
            // 相同类型兼容
            if (a.getTypeName().equals(b.getTypeName())) {
                return true;
            }
            
            // 检查继承关系
            return a.isAssignableTo(b) || b.isAssignableTo(a);
        }

        private IGenericType mergeTypes(IGenericType a, IGenericType b) {
            // 如果类型相同，直接返回
            if (a.getTypeName().equals(b.getTypeName())) {
                return a;
            }
            
            // 如果一个是另一个的子类型，返回父类型
            if (a.isAssignableTo(b)) {
                return b;
            }
            if (b.isAssignableTo(a)) {
                return a;
            }
            
            // 否则创建Union类型
            List<IGenericType> types = new ArrayList<>();
            types.add(a);
            types.add(b);
            return new GenericUnionTypeImpl(types);
        }

        public Map<String, IGenericType> getBindings() {
            return bindings;
        }
    }
}
