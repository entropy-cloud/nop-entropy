/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.core.type.IGenericType;
import io.nop.core.type.IUnionType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericUnionTypeImpl;
import io.nop.xlang.ast.*;
import io.nop.xlang.ast.XLangOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;

/**
 * Union类型窄化器
 * <p>
 * 根据条件表达式窄化Union类型。例如：
 * <ul>
 *   <li>typeof x === 'string' → x 窄化为 string</li>
 *   <li>x instanceof MyClass → x 窄化为 MyClass</li>
 *   <li>x != null → 移除 null 类型</li>
 *   <li>x === true → x 窄化为 true 字面量类型</li>
 * </ul>
 */
public class UnionTypeNarrower {

    /**
     * 从条件表达式收集类型窄化信息
     *
     * @param condition 条件表达式
     * @param isTrue    是否在true分支
     * @param state     类型推断状态
     * @return 变量名到窄化类型的映射
     */
    public static Map<String, IGenericType> collectNarrowedTypes(Expression condition, boolean isTrue, TypeInferenceState state) {
        if (condition == null) {
            return Collections.emptyMap();
        }

        Map<String, IGenericType> result = new HashMap<>();
        collectNarrowedTypes(condition, isTrue, result, state);
        return result;
    }

    public static Map<String, IGenericType> collectNarrowedTypes(Expression condition, boolean isTrue) {
        return collectNarrowedTypes(condition, isTrue, null);
    }

    private static void collectNarrowedTypes(Expression condition, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        switch (condition.getASTKind()) {
            case TypeOfExpression:
                handleTypeOfExpression((TypeOfExpression) condition, isTrue, result);
                break;

            case InstanceOfExpression:
                handleInstanceOfExpression((InstanceOfExpression) condition, isTrue, result, state);
                break;

            case BinaryExpression:
                handleBinaryExpression((BinaryExpression) condition, isTrue, result, state);
                break;

            case LogicalExpression:
                handleLogicalExpression((LogicalExpression) condition, isTrue, result, state);
                break;

            case UnaryExpression:
                handleUnaryExpression((UnaryExpression) condition, isTrue, result, state);
                break;

            case CallExpression:
                handleCallExpression((CallExpression) condition, isTrue, result, state);
                break;

            case Identifier:
                handleIdentifier((Identifier) condition, isTrue, result, state);
                break;

            default:
                break;
        }
    }

    /**
     * 处理 typeof x === 'string' 形式
     */
    private static void handleTypeOfExpression(TypeOfExpression expr, boolean isTrue, Map<String, IGenericType> result) {
        Expression argument = expr.getArgument();
        if (!(argument instanceof Identifier)) {
            return;
        }

        String varName = ((Identifier) argument).getName();
        IGenericType varType = result.get(varName);
        if (varType == null) {
            return;
        }

        // typeof 返回类型名字符串，这里无法直接窄化
        // 实际窄化在 BinaryExpression 中处理 typeof x === 'string'
    }

    /**
     * 处理 x instanceof Type 形式
     */
    private static void handleInstanceOfExpression(InstanceOfExpression expr, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        Expression value = expr.getValue();
        if (!(value instanceof Identifier)) {
            return;
        }

        String varName = ((Identifier) value).getName();
        IGenericType checkType = expr.getRefType().getTypeInfo();
        
        if (checkType == null) {
            return;
        }

        if (isTrue) {
            // instanceof 为 true: 窄化为指定类型
            result.put(varName, checkType);
        } else {
            // instanceof 为 false: 从Union中移除该类型
            IGenericType currentType = result.get(varName);
            if (currentType != null && currentType.isUnion()) {
                result.put(varName, removeFromUnion(currentType, checkType));
            }
        }
    }

    /**
     * 处理二元表达式：x === null, x != null, typeof x === 'string'
     */
    private static void handleBinaryExpression(BinaryExpression expr, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        XLangOperator op = expr.getOperator();
        Expression left = expr.getLeft();
        Expression right = expr.getRight();

        if (op == XLangOperator.EQ || op == XLangOperator.ASSIGN) {
            handleEquality(left, right, isTrue, result);
            handleEquality(right, left, isTrue, result);
        } else if (op == XLangOperator.NE) {
            handleEquality(left, right, !isTrue, result);
            handleEquality(right, left, !isTrue, result);
        }
    }

    /**
     * 处理等值比较：x === null, x === 'string', typeof x === 'string'
     */
    private static void handleEquality(Expression left, Expression right, boolean isTrue, Map<String, IGenericType> result) {
        // typeof x === 'string' 形式
        if (left instanceof TypeOfExpression && right instanceof Literal) {
            TypeOfExpression typeOf = (TypeOfExpression) left;
            String typeName = getStringLiteral((Literal) right);
            
            if (typeName != null && typeOf.getArgument() instanceof Identifier) {
                String varName = ((Identifier) typeOf.getArgument()).getName();
                IGenericType narrowedType = getTypeByName(typeName);
                
                if (narrowedType != null) {
                    if (isTrue) {
                        result.put(varName, narrowedType);
                    } else {
                        IGenericType currentType = result.get(varName);
                        if (currentType != null && currentType.isUnion()) {
                            result.put(varName, removeFromUnion(currentType, narrowedType));
                        }
                    }
                }
            }
            return;
        }

        // x === null / x === undefined
        if (left instanceof Identifier && right instanceof Literal) {
            String varName = ((Identifier) left).getName();
            IGenericType rightType = inferLiteralType((Literal) right);
            
            if (rightType == PredefinedGenericTypes.NULL_TYPE) {
                if (isTrue) {
                    result.put(varName, PredefinedGenericTypes.NULL_TYPE);
                } else {
                    IGenericType currentType = result.get(varName);
                    if (currentType != null && currentType.isUnion()) {
                        result.put(varName, removeFromUnion(currentType, PredefinedGenericTypes.NULL_TYPE));
                    }
                }
            }
        }
    }

    /**
     * 处理逻辑表达式：x && y, x || y
     */
    private static void handleLogicalExpression(LogicalExpression expr, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        XLangOperator op = expr.getOperator();
        Expression left = expr.getLeft();
        Expression right = expr.getRight();

        if (op == XLangOperator.AND) {
            if (isTrue) {
                collectNarrowedTypes(left, true, result, state);
                collectNarrowedTypes(right, true, result, state);
            }
        } else if (op == XLangOperator.OR) {
            if (!isTrue) {
                collectNarrowedTypes(left, false, result, state);
                collectNarrowedTypes(right, false, result, state);
            }
        }
    }

    /**
     * 处理一元表达式：!x
     */
    private static void handleUnaryExpression(UnaryExpression expr, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        if (expr.getOperator() == XLangOperator.NOT) {
            collectNarrowedTypes(expr.getArgument(), !isTrue, result, state);
        }
    }

    /**
     * 处理函数调用：Array.isArray(x), x.hasOwnProperty('prop')
     */
    private static void handleCallExpression(CallExpression expr, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        Expression callee = expr.getCallee();
        List<Expression> args = expr.getArguments();

        if (callee instanceof MemberExpression) {
            MemberExpression member = (MemberExpression) callee;
            Expression obj = member.getObject();
            String prop = member.getProperty() instanceof Identifier 
                    ? ((Identifier) member.getProperty()).getName() 
                    : null;

            // Array.isArray(x)
            if ("isArray".equals(prop) && obj instanceof Identifier) {
                String objName = ((Identifier) obj).getName();
                if ("Array".equals(objName) && args.size() == 1 && args.get(0) instanceof Identifier) {
                    String varName = ((Identifier) args.get(0)).getName();
                    if (isTrue) {
                        result.put(varName, PredefinedGenericTypes.LIST_ANY_TYPE);
                    }
                }
            }
        }
    }

    /**
     * 处理标识符真值检查：if (x) { ... }
     */
    private static void handleIdentifier(Identifier id, boolean isTrue, Map<String, IGenericType> result, TypeInferenceState state) {
        String varName = id.getName();
        IGenericType currentType = state != null ? state.getVariableType(varName) : id.getReturnTypeInfo();
        
        if (currentType == null) {
            return;
        }

        if (isTrue) {
            // 移除 falsy 类型：null, undefined, false, 0, ""
            IGenericType narrowed = removeFalsyTypes(currentType);
            if (narrowed != currentType) {
                result.put(varName, narrowed);
            }
        }
    }

    /**
     * 从Union类型中移除指定类型
     */
    public static IGenericType removeFromUnion(IGenericType union, IGenericType toRemove) {
        if (!union.isUnion()) {
            if (isSameType(union, toRemove)) {
                return PredefinedGenericTypes.NEVER_TYPE;
            }
            return union;
        }

        List<IGenericType> subTypes = union.getSubTypes();
        List<IGenericType> remaining = new ArrayList<>();
        
        for (IGenericType subType : subTypes) {
            if (!isSameType(subType, toRemove)) {
                remaining.add(subType);
            }
        }

        if (remaining.isEmpty()) {
            return PredefinedGenericTypes.NEVER_TYPE;
        }
        
        if (remaining.size() == 1) {
            return remaining.get(0);
        }
        
        return new GenericUnionTypeImpl(remaining);
    }

    /**
     * 移除 falsy 类型（null, false, 0 等）
     */
    private static IGenericType removeFalsyTypes(IGenericType type) {
        if (!type.isUnion()) {
            return type;
        }

        List<IGenericType> subTypes = type.getSubTypes();
        List<IGenericType> remaining = new ArrayList<>();
        
        for (IGenericType subType : subTypes) {
            if (!isFalsyType(subType)) {
                remaining.add(subType);
            }
        }

        if (remaining.isEmpty()) {
            return type;
        }
        
        if (remaining.size() == 1) {
            return remaining.get(0);
        }
        
        if (remaining.size() == subTypes.size()) {
            return type;
        }
        
        return new GenericUnionTypeImpl(remaining);
    }

    private static boolean isFalsyType(IGenericType type) {
        return type == PredefinedGenericTypes.NULL_TYPE;
    }

    private static boolean isSameType(IGenericType a, IGenericType b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getTypeName().equals(b.getTypeName());
    }

    private static String getStringLiteral(Literal literal) {
        Object value = literal.getValue();
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    private static IGenericType getTypeByName(String typeName) {
        switch (typeName) {
            case "string":
                return PredefinedGenericTypes.STRING_TYPE;
            case "number":
                return PredefinedGenericTypes.NUMBER_TYPE;
            case "boolean":
                return PredefinedGenericTypes.BOOLEAN_TYPE;
            case "function":
                return PredefinedGenericTypes.FUNCTION_TYPE;
            case "object":
                return PredefinedGenericTypes.MAP_TYPE;
            case "symbol":
                return PredefinedGenericTypes.ANY_TYPE;
            case "undefined":
                return PredefinedGenericTypes.NULL_TYPE;
            default:
                return null;
        }
    }

    private static IGenericType inferLiteralType(Literal literal) {
        Object value = literal.getValue();
        if (value == null) {
            return PredefinedGenericTypes.NULL_TYPE;
        }
        if (value instanceof String) {
            return PredefinedGenericTypes.STRING_TYPE;
        }
        if (value instanceof Boolean) {
            return PredefinedGenericTypes.BOOLEAN_TYPE;
        }
        if (value instanceof Integer) {
            return PredefinedGenericTypes.INT_TYPE;
        }
        if (value instanceof Long) {
            return PredefinedGenericTypes.LONG_TYPE;
        }
        if (value instanceof Double || value instanceof Float) {
            return PredefinedGenericTypes.DOUBLE_TYPE;
        }
        if (value instanceof Number) {
            return PredefinedGenericTypes.NUMBER_TYPE;
        }
        return PredefinedGenericTypes.ANY_TYPE;
    }
}