/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IUnionType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericUnionTypeImpl;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.xlang.ast.ArrayBinding;
import io.nop.xlang.ast.ArrayElementBinding;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.AwaitExpression;
import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.BlockStatement;
import io.nop.xlang.ast.BraceExpression;
import io.nop.xlang.ast.BreakStatement;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.CastExpression;
import io.nop.xlang.ast.CatchClause;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.CollectOutputExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.ContinueStatement;
import io.nop.xlang.ast.DeleteStatement;
import io.nop.xlang.ast.DoWhileStatement;
import io.nop.xlang.ast.EmptyStatement;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.ExpressionStatement;
import io.nop.xlang.ast.ForInStatement;
import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.ForStatement;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.GenNodeAttrExpression;
import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.ImportDefaultSpecifier;
import io.nop.xlang.ast.ImportNamespaceSpecifier;
import io.nop.xlang.ast.InExpression;
import io.nop.xlang.ast.InstanceOfExpression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.ObjectBinding;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.PropertyBinding;
import io.nop.xlang.ast.RegExpLiteral;
import io.nop.xlang.ast.RestBinding;
import io.nop.xlang.ast.ReturnStatement;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.SpreadElement;
import io.nop.xlang.ast.SuperExpression;
import io.nop.xlang.ast.SwitchCase;
import io.nop.xlang.ast.TemplateStringExpression;
import io.nop.xlang.ast.TemplateStringLiteral;
import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.ast.ThisExpression;
import io.nop.xlang.ast.ThrowStatement;
import io.nop.xlang.ast.TryStatement;
import io.nop.xlang.ast.TypeOfExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.UsingStatement;
import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.VariableDeclarator;
import io.nop.xlang.ast.WhileStatement;
import io.nop.xlang.ast.XLangASTProcessor;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.Expression;

import java.util.ArrayList;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

public class TypeInferenceProcessor extends XLangASTProcessor<ReturnTypeInfo, TypeInferenceState> {

    private final TypeErrorCollector errors = new TypeErrorCollector();

    public TypeErrorCollector getErrors() {
        return errors;
    }

    @Override
    public ReturnTypeInfo processIfStatement(IfStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getTest(), context);
        TypeInferenceState s1 = context.newChild();

        // Collect narrowed types from condition for true branch
        Map<String, IGenericType> narrowedTypes = UnionTypeNarrower.collectNarrowedTypes(node.getTest(), true, context);
        s1.setNarrowedTypes(narrowedTypes);

        ReturnTypeInfo r1 = processAST(node.getConsequent(), s1);

        if (node.getAlternate() != null) {
            TypeInferenceState s2 = context.newChild();
            // Collect narrowed types from condition for false branch (negated)
            Map<String, IGenericType> narrowedTypesElse = UnionTypeNarrower.collectNarrowedTypes(node.getTest(), false, context);
            s2.setNarrowedTypes(narrowedTypesElse);
            ReturnTypeInfo r2 = processAST(node.getAlternate(), s2);
            r1 = union(r1, r2);
        } else {
            r1 = union(r1, null);
        }

        return r1;
    }

    ReturnTypeInfo union(ReturnTypeInfo r1, ReturnTypeInfo r2) {
        if (r1 == null && r2 == null)
            return null;

        if (r1 == null) {
            r2.setOtherBranchNoReturn(true);
            return r2;
        }

        if (r2 == null) {
            r1.setOtherBranchNoReturn(true);
            return r1;
        }

        // 两个分支都有返回，合并类型
        IGenericType mergedType = mergeTypes(r1.getReturnType(), r2.getReturnType());
        
        ReturnTypeInfo result = new ReturnTypeInfo();
        result.setReturnType(mergedType);
        // 两个分支都返回，所以 otherBranchNoReturn = false (默认值)
        return result;
    }

    private IGenericType mergeTypes(IGenericType t1, IGenericType t2) {
        if (t1 == null && t2 == null)
            return PredefinedGenericTypes.ANY_TYPE;
        if (t1 == null)
            return t2;
        if (t2 == null)
            return t1;
        
        // 相同类型直接返回
        if (t1.equals(t2))
            return t1;
        
        String name1 = t1.getTypeName();
        String name2 = t2.getTypeName();
        if (name1 != null && name1.equals(name2))
            return t1;
        
        // 创建 Union 类型，展平已有的 union 类型并去重
        List<IGenericType> types = new ArrayList<>(2);
        Set<String> seen = new HashSet<>();
        flattenUnionTypes(t1, types, seen);
        flattenUnionTypes(t2, types, seen);

        if (types.isEmpty()) {
            return PredefinedGenericTypes.ANY_TYPE;
        }
        if (types.size() == 1) {
            return types.get(0);
        }
        return new GenericUnionTypeImpl(types);
    }

    private void flattenUnionTypes(IGenericType type, List<IGenericType> result, Set<String> seen) {
        if (type instanceof IUnionType) {
            for (IGenericType subType : ((IUnionType) type).getSubTypes()) {
                flattenUnionTypes(subType, result, seen);
            }
        } else {
            String typeName = type.getTypeName();
            if (typeName != null && !seen.contains(typeName)) {
                seen.add(typeName);
                result.add(type);
            }
        }
    }

    @Override
    public ReturnTypeInfo processLogicalExpression(LogicalExpression node, TypeInferenceState context) {
        ReturnTypeInfo r1 = processAST(node.getLeft(), context);
        ReturnTypeInfo r2 = processAST(node.getRight(), context.newChild());
        return union(r1, r2);
    }

    @Override
    public ReturnTypeInfo processProgram(Program node, TypeInferenceState context) {
        return super.processProgram(node, context);
    }

    @Override
    public ReturnTypeInfo processIdentifier(Identifier node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType type = context.getVariableType(node.getName());
        if (type != null) {
            node.setReturnTypeInfo(type);
            ReturnTypeInfo info = new ReturnTypeInfo();
            info.setReturnType(type);
            return info;
        }
        return null;
    }

    @Override
    public ReturnTypeInfo processLiteral(Literal node, TypeInferenceState context) {
        IGenericType type = inferLiteralType(node);
        node.setReturnTypeInfo(type);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(type);
        return info;
    }

    private IGenericType inferLiteralType(Literal node) {
        Object value = node.getValue();
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

    @Override
    public ReturnTypeInfo processTemplateStringLiteral(TemplateStringLiteral node, TypeInferenceState context) {
        return super.processTemplateStringLiteral(node, context);
    }

    @Override
    public ReturnTypeInfo processRegExpLiteral(RegExpLiteral node, TypeInferenceState context) {
        return super.processRegExpLiteral(node, context);
    }

    @Override
    public ReturnTypeInfo processBlockStatement(BlockStatement node, TypeInferenceState context) {
        return super.processBlockStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processEmptyStatement(EmptyStatement node, TypeInferenceState context) {
        return super.processEmptyStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processReturnStatement(ReturnStatement node, TypeInferenceState context) {
        return super.processReturnStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processBreakStatement(BreakStatement node, TypeInferenceState context) {
        return super.processBreakStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processContinueStatement(ContinueStatement node, TypeInferenceState context) {
        return super.processContinueStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processSwitchCase(SwitchCase node, TypeInferenceState context) {
        return super.processSwitchCase(node, context);
    }

    @Override
    public ReturnTypeInfo processThrowStatement(ThrowStatement node, TypeInferenceState context) {
        return super.processThrowStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processTryStatement(TryStatement node, TypeInferenceState context) {
        return super.processTryStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processCatchClause(CatchClause node, TypeInferenceState context) {
        return super.processCatchClause(node, context);
    }

    @Override
    public ReturnTypeInfo processWhileStatement(WhileStatement node, TypeInferenceState context) {
        return super.processWhileStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processDoWhileStatement(DoWhileStatement node, TypeInferenceState context) {
        return super.processDoWhileStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processVariableDeclaration(VariableDeclaration node, TypeInferenceState context) {
        return super.processVariableDeclaration(node, context);
    }

    @Override
    public ReturnTypeInfo processForStatement(ForStatement node, TypeInferenceState context) {
        return super.processForStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processForOfStatement(ForOfStatement node, TypeInferenceState context) {
        return super.processForOfStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processForInStatement(ForInStatement node, TypeInferenceState context) {
        return super.processForInStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processDeleteStatement(DeleteStatement node, TypeInferenceState context) {
        return super.processDeleteStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processChainExpression(ChainExpression node, TypeInferenceState context) {
        return super.processChainExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processThisExpression(ThisExpression node, TypeInferenceState context) {
        return super.processThisExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processSuperExpression(SuperExpression node, TypeInferenceState context) {
        return super.processSuperExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processTemplateStringExpression(TemplateStringExpression node, TypeInferenceState context) {
        return super.processTemplateStringExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processArrayExpression(ArrayExpression node, TypeInferenceState context) {
        return super.processArrayExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processObjectExpression(ObjectExpression node, TypeInferenceState context) {
        return super.processObjectExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processPropertyAssignment(PropertyAssignment node, TypeInferenceState context) {
        return super.processPropertyAssignment(node, context);
    }

    @Override
    public ReturnTypeInfo processParameterDeclaration(ParameterDeclaration node, TypeInferenceState context) {
        return super.processParameterDeclaration(node, context);
    }

    @Override
    public ReturnTypeInfo processFunctionDeclaration(FunctionDeclaration node, TypeInferenceState context) {
        return super.processFunctionDeclaration(node, context);
    }

    @Override
    public ReturnTypeInfo processArrowFunctionExpression(ArrowFunctionExpression node, TypeInferenceState context) {
        return super.processArrowFunctionExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processUnaryExpression(UnaryExpression node, TypeInferenceState context) {
        return super.processUnaryExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processUpdateExpression(UpdateExpression node, TypeInferenceState context) {
        return super.processUpdateExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processBinaryExpression(BinaryExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo leftInfo = processAST(node.getLeft(), context);
        ReturnTypeInfo rightInfo = processAST(node.getRight(), context);

        IGenericType leftType = leftInfo != null ? leftInfo.getReturnType() : null;
        IGenericType rightType = rightInfo != null ? rightInfo.getReturnType() : null;

        IGenericType resultType = inferBinaryType(node.getOperator(), leftType, rightType);
        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    private IGenericType inferBinaryType(XLangOperator op, IGenericType left, IGenericType right) {
        if (left == null || right == null) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        if (op == XLangOperator.ADD) {
            if (left == PredefinedGenericTypes.STRING_TYPE || right == PredefinedGenericTypes.STRING_TYPE) {
                return PredefinedGenericTypes.STRING_TYPE;
            }
            if (left.isNumericType() && right.isNumericType()) {
                return promoteNumericTypes(left, right);
            }
            return PredefinedGenericTypes.ANY_TYPE;
        }

        if (op == XLangOperator.MINUS || op == XLangOperator.DIVIDE || op == XLangOperator.MULTIPLY || op == XLangOperator.MOD) {
            if (left.isNumericType() && right.isNumericType()) {
                return promoteNumericTypes(left, right);
            }
            return PredefinedGenericTypes.ANY_TYPE;
        }

        if (op == XLangOperator.LT || op == XLangOperator.LE || op == XLangOperator.GT || op == XLangOperator.GE
                || op == XLangOperator.EQ || op == XLangOperator.NE) {
            return PredefinedGenericTypes.BOOLEAN_TYPE;
        }

        if (op == XLangOperator.AND || op == XLangOperator.OR) {
            return PredefinedGenericTypes.BOOLEAN_TYPE;
        }

        return PredefinedGenericTypes.ANY_TYPE;
    }

    private IGenericType promoteNumericTypes(IGenericType a, IGenericType b) {
        if (a == PredefinedGenericTypes.DOUBLE_TYPE || b == PredefinedGenericTypes.DOUBLE_TYPE) {
            return PredefinedGenericTypes.DOUBLE_TYPE;
        }
        if (a == PredefinedGenericTypes.FLOAT_TYPE || b == PredefinedGenericTypes.FLOAT_TYPE) {
            return PredefinedGenericTypes.FLOAT_TYPE;
        }
        if (a == PredefinedGenericTypes.LONG_TYPE || b == PredefinedGenericTypes.LONG_TYPE) {
            return PredefinedGenericTypes.LONG_TYPE;
        }
        return PredefinedGenericTypes.INT_TYPE;
    }

    @Override
    public ReturnTypeInfo processInExpression(InExpression node, TypeInferenceState context) {
        return super.processInExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processExpressionStatement(ExpressionStatement node, TypeInferenceState context) {
        return super.processExpressionStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processAssignmentExpression(AssignmentExpression node, TypeInferenceState context) {
        return super.processAssignmentExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processMemberExpression(MemberExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo objInfo = processAST(node.getObject(), context);
        IGenericType objType = objInfo != null ? objInfo.getReturnType() : null;

        IGenericType resultType = PredefinedGenericTypes.ANY_TYPE;

        if (objType != null) {
            Expression prop = node.getProperty();
            if (prop instanceof Identifier) {
                String propName = ((Identifier) prop).getName();
                resultType = inferMemberType(objType, propName);
            }
        }

        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    private IGenericType inferMemberType(IGenericType objType, String propName) {
        if (objType.isListLike()) {
            if ("length".equals(propName)) {
                return PredefinedGenericTypes.INT_TYPE;
            }
            if ("size".equals(propName)) {
                return PredefinedGenericTypes.INT_TYPE;
            }
            return objType.getComponentType();
        }

        if (objType.isMapLike()) {
            if ("size".equals(propName) || "length".equals(propName)) {
                return PredefinedGenericTypes.INT_TYPE;
            }
            IGenericType valueType = objType.getMapValueType();
            return valueType != null ? valueType : PredefinedGenericTypes.ANY_TYPE;
        }

        if (objType == PredefinedGenericTypes.STRING_TYPE) {
            if ("length".equals(propName)) {
                return PredefinedGenericTypes.INT_TYPE;
            }
        }

        // Use ReflectionManager with caching for class member access
        if (objType.isResolved() && objType.getRawClass() != null) {
            Class<?> rawClass = objType.getRawClass();
            IClassModel classModel = ReflectionManager.instance().getClassModel(rawClass);
            
            // Try bean property first (handles getter methods)
            IBeanModel beanModel = classModel.getBeanModel();
            if (beanModel != null) {
                IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
                if (propModel != null && propModel.getType() != null) {
                    return propModel.getType();
                }
            }
            
            // Try public field
            io.nop.core.reflect.IFieldModel fieldModel = classModel.getField(propName);
            if (fieldModel != null) {
                return fieldModel.getType();
            }
        }

        return PredefinedGenericTypes.ANY_TYPE;
    }

    @Override
    public ReturnTypeInfo processVariableDeclarator(VariableDeclarator node, TypeInferenceState context) {
        if (context == null)
            return null;

        // Process initializer
        ReturnTypeInfo initInfo = null;
        if (node.getInit() != null) {
            initInfo = processAST(node.getInit(), context);
        }

        // Determine variable type
        IGenericType varType = null;
        IGenericType initType = initInfo != null ? initInfo.getReturnType() : null;

        // Check explicit type annotation
        if (node.getVarType() != null && node.getVarType().getTypeInfo() != null) {
            varType = node.getVarType().getTypeInfo();
            
            // Check type compatibility between explicit type and initializer type
            if (initType != null && !isTypeCompatible(varType, initType)) {
                errors.typeMismatch(node.getLocation(), varType, initType);
            }
        } else if (initType != null) {
            // Infer from initializer
            varType = initType;
        }

        if (varType == null) {
            varType = PredefinedGenericTypes.ANY_TYPE;
        }

        // Register variable in scope
        io.nop.xlang.ast.XLangASTNode id = node.getId();
        if (id instanceof Identifier) {
            Identifier idNode = (Identifier) id;
            context.setVariableType(idNode.getName(), varType);
            idNode.setReturnTypeInfo(varType);
        }

        return initInfo;
    }

    private boolean isTypeCompatible(IGenericType expectedType, IGenericType actualType) {
        if (expectedType == null || actualType == null) {
            return true;
        }
        if (expectedType.isAnyType() || actualType.isAnyType()) {
            return true;
        }
        return actualType.isAssignableTo(expectedType);
    }

    @Override
    public ReturnTypeInfo processCallExpression(CallExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo calleeInfo = processAST(node.getCallee(), context);

        // Process arguments
        List<Expression> args = node.getArguments();
        if (args != null) {
            for (Expression arg : args) {
                processAST(arg, context);
            }
        }

        // Infer return type from callee
        IGenericType returnType = null;
        if (calleeInfo != null) {
            IGenericType calleeType = calleeInfo.getReturnType();
            if (calleeType != null && calleeType.isFunction()) {
                returnType = calleeType.getFuncReturnType();

                // Generic type inference
                if (returnType != null && returnType.containsTypeVariable()) {
                    List<IGenericType> paramTypes = calleeType.getFuncArgTypes();
                    List<IGenericType> argTypes = new java.util.ArrayList<>();
                    if (args != null) {
                        for (Expression arg : args) {
                            argTypes.add(arg.getReturnTypeInfo() != null ? arg.getReturnTypeInfo() : PredefinedGenericTypes.ANY_TYPE);
                        }
                    }
                    Map<String, IGenericType> typeArgs = GenericTypeInferencer.inferTypeArguments(
                            calleeType.getTypeParameters(), paramTypes, argTypes, errors);
                    returnType = GenericTypeInferencer.applyTypeArguments(returnType, typeArgs);
                }
            }
        }

        if (returnType == null) {
            returnType = PredefinedGenericTypes.ANY_TYPE;
        }

        node.setReturnTypeInfo(returnType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(returnType);
        return info;
    }

    @Override
    public ReturnTypeInfo processNewExpression(NewExpression node, TypeInferenceState context) {
        return super.processNewExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processSpreadElement(SpreadElement node, TypeInferenceState context) {
        return super.processSpreadElement(node, context);
    }

    @Override
    public ReturnTypeInfo processSequenceExpression(SequenceExpression node, TypeInferenceState context) {
        return super.processSequenceExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processConcatExpression(ConcatExpression node, TypeInferenceState context) {
        return super.processConcatExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processBraceExpression(BraceExpression node, TypeInferenceState context) {
        return super.processBraceExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processObjectBinding(ObjectBinding node, TypeInferenceState context) {
        return super.processObjectBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processPropertyBinding(PropertyBinding node, TypeInferenceState context) {
        return super.processPropertyBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processRestBinding(RestBinding node, TypeInferenceState context) {
        return super.processRestBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processArrayBinding(ArrayBinding node, TypeInferenceState context) {
        return super.processArrayBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processArrayElementBinding(ArrayElementBinding node, TypeInferenceState context) {
        return super.processArrayElementBinding(node, context);
    }

    @Override
    public ReturnTypeInfo processImportDefaultSpecifier(ImportDefaultSpecifier node, TypeInferenceState context) {
        return super.processImportDefaultSpecifier(node, context);
    }

    @Override
    public ReturnTypeInfo processImportNamespaceSpecifier(ImportNamespaceSpecifier node, TypeInferenceState context) {
        return super.processImportNamespaceSpecifier(node, context);
    }

    @Override
    public ReturnTypeInfo processAwaitExpression(AwaitExpression node, TypeInferenceState context) {
        return super.processAwaitExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processUsingStatement(UsingStatement node, TypeInferenceState context) {
        return super.processUsingStatement(node, context);
    }

    @Override
    public ReturnTypeInfo processTextOutputExpression(TextOutputExpression node, TypeInferenceState context) {
        return super.processTextOutputExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processEscapeOutputExpression(EscapeOutputExpression node, TypeInferenceState context) {
        return super.processEscapeOutputExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCollectOutputExpression(CollectOutputExpression node, TypeInferenceState context) {
        return super.processCollectOutputExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCompareOpExpression(CompareOpExpression node, TypeInferenceState context) {
        return super.processCompareOpExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processAssertOpExpression(AssertOpExpression node, TypeInferenceState context) {
        return super.processAssertOpExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processBetweenOpExpression(BetweenOpExpression node, TypeInferenceState context) {
        return super.processBetweenOpExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processGenNodeExpression(GenNodeExpression node, TypeInferenceState context) {
        return super.processGenNodeExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processGenNodeAttrExpression(GenNodeAttrExpression node, TypeInferenceState context) {
        return super.processGenNodeAttrExpression(node, context);
    }


    @Override
    public ReturnTypeInfo processTypeOfExpression(TypeOfExpression node, TypeInferenceState context) {
        return super.processTypeOfExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processInstanceOfExpression(InstanceOfExpression node, TypeInferenceState context) {
        return super.processInstanceOfExpression(node, context);
    }

    @Override
    public ReturnTypeInfo processCastExpression(CastExpression node, TypeInferenceState context) {
        return super.processCastExpression(node, context);
    }

}