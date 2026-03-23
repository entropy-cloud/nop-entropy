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
import io.nop.core.type.impl.GenericFunctionTypeImpl;
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
import io.nop.xlang.ast.FunctionExpression;
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
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangASTProcessor;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.Expression;

import java.util.ArrayList;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.commons.text.regex.IRegex;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;
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
            mergeConditionalVariableTypes(context, s1, s2, true);
            r1 = union(r1, r2);
        } else {
            mergeConditionalVariableTypes(context, s1, null, false);
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
        return processSequentialNodes(node.getBody(), context);
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
        node.setReturnTypeInfo(PredefinedGenericTypes.STRING_TYPE);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.STRING_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processRegExpLiteral(RegExpLiteral node, TypeInferenceState context) {
        IGenericType regexType = ReflectionManager.instance().buildRawType(IRegex.class);
        node.setReturnTypeInfo(regexType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(regexType);
        return info;
    }

    @Override
    public ReturnTypeInfo processBlockStatement(BlockStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        return processSequentialNodes(node.getBody(), context.newChild());
    }

    @Override
    public ReturnTypeInfo processEmptyStatement(EmptyStatement node, TypeInferenceState context) {
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processReturnStatement(ReturnStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo argInfo = processAST(node.getArgument(), context);
        IGenericType actualType = argInfo != null ? argInfo.getReturnType() : PredefinedGenericTypes.VOID_TYPE;
        IGenericType expectedType = context.getCurrentReturnType();
        if (expectedType != null && !isTypeCompatible(expectedType, actualType)) {
            errors.typeMismatch(node.getLocation(), expectedType, actualType);
        }

        node.setReturnTypeInfo(actualType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(actualType);
        info.setReturnAST(node.getArgument());
        return info;
    }

    @Override
    public ReturnTypeInfo processBreakStatement(BreakStatement node, TypeInferenceState context) {
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processContinueStatement(ContinueStatement node, TypeInferenceState context) {
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processSwitchCase(SwitchCase node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getTest(), context);
        return processAST(node.getConsequent(), context.newChild());
    }

    @Override
    public ReturnTypeInfo processThrowStatement(ThrowStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo argInfo = processAST(node.getArgument(), context);
        IGenericType resultType = argInfo != null ? argInfo.getReturnType() : PredefinedGenericTypes.ANY_TYPE;
        node.setReturnTypeInfo(resultType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processTryStatement(TryStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo blockInfo = processAST(node.getBlock(), context.newChild());
        ReturnTypeInfo catchInfo = processAST(node.getCatchHandler(), context.newChild());
        ReturnTypeInfo finalizerInfo = processAST(node.getFinalizer(), context.newChild());
        return union(union(blockInfo, catchInfo), finalizerInfo);
    }

    @Override
    public ReturnTypeInfo processCatchClause(CatchClause node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType catchType = getDeclaredType(node.getVarType());
        if (catchType == null) {
            catchType = PredefinedGenericTypes.ANY_TYPE;
        }

        TypeInferenceState catchState = context.newChild();
        if (node.getName() != null) {
            catchState.setVariableType(node.getName().getName(), catchType);
            node.getName().setReturnTypeInfo(catchType);
        }

        return processAST(node.getBody(), catchState);
    }

    @Override
    public ReturnTypeInfo processWhileStatement(WhileStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getTest(), context);
        return processLoopBody(node.getBody(), context.newChild());
    }

    @Override
    public ReturnTypeInfo processDoWhileStatement(DoWhileStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        TypeInferenceState loopState = context.newChild();
        ReturnTypeInfo bodyInfo = processLoopBody(node.getBody(), loopState);
        processAST(node.getTest(), loopState);
        return bodyInfo;
    }

    @Override
    public ReturnTypeInfo processVariableDeclaration(VariableDeclaration node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo result = null;
        if (node.getDeclarators() != null) {
            for (VariableDeclarator declarator : node.getDeclarators()) {
                ReturnTypeInfo info = processAST(declarator, context);
                result = union(result, info);
            }
        }
        return result;
    }

    @Override
    public ReturnTypeInfo processForStatement(ForStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        TypeInferenceState loopState = context.newChild();
        processAST(node.getInit(), loopState);
        processAST(node.getTest(), loopState);
        ReturnTypeInfo bodyInfo = processLoopBody(node.getBody(), loopState);
        processAST(node.getUpdate(), loopState);
        return bodyInfo;
    }

    @Override
    public ReturnTypeInfo processForOfStatement(ForOfStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo rightInfo = processAST(node.getRight(), context);
        IGenericType rightType = rightInfo != null ? rightInfo.getReturnType() : null;

        TypeInferenceState loopState = context.newChild();
        IGenericType elementType = getSpreadElementType(rightType);
        bindLoopVariable(node.getLeft(), elementType, loopState);
        if (node.getIndex() != null) {
            loopState.setVariableType(node.getIndex().getName(), PredefinedGenericTypes.INT_TYPE);
            node.getIndex().setReturnTypeInfo(PredefinedGenericTypes.INT_TYPE);
        }

        return processAST(node.getBody(), loopState);
    }

    @Override
    public ReturnTypeInfo processForInStatement(ForInStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getRight(), context);

        TypeInferenceState loopState = context.newChild();
        bindLoopVariable(node.getLeft(), PredefinedGenericTypes.STRING_TYPE, loopState);
        if (node.getIndex() != null) {
            loopState.setVariableType(node.getIndex().getName(), PredefinedGenericTypes.INT_TYPE);
            node.getIndex().setReturnTypeInfo(PredefinedGenericTypes.INT_TYPE);
        }

        return processAST(node.getBody(), loopState);
    }

    @Override
    public ReturnTypeInfo processDeleteStatement(DeleteStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getArgument(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.BOOLEAN_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processChainExpression(ChainExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo exprInfo = processAST(node.getExpr(), context);
        IGenericType resultType = exprInfo != null ? exprInfo.getReturnType() : PredefinedGenericTypes.ANY_TYPE;
        node.setReturnTypeInfo(resultType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processThisExpression(ThisExpression node, TypeInferenceState context) {
        node.setReturnTypeInfo(PredefinedGenericTypes.ANY_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.ANY_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processSuperExpression(SuperExpression node, TypeInferenceState context) {
        node.setReturnTypeInfo(PredefinedGenericTypes.ANY_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.ANY_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processTemplateStringExpression(TemplateStringExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getId(), context);
        processAST(node.getValue(), context);

        node.setReturnTypeInfo(PredefinedGenericTypes.STRING_TYPE);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.STRING_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processArrayExpression(ArrayExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType elementType = null;
        if (node.getElements() != null) {
            for (XLangASTNode element : node.getElements()) {
                IGenericType currentType;
                if (element instanceof SpreadElement) {
                    ReturnTypeInfo spreadInfo = processAST(((SpreadElement) element).getArgument(), context);
                    currentType = getSpreadElementType(spreadInfo != null ? spreadInfo.getReturnType() : null);
                } else {
                    ReturnTypeInfo elementInfo = processAST(element, context);
                    currentType = elementInfo != null ? elementInfo.getReturnType() : null;
                }
                elementType = mergeTypes(elementType, currentType);
            }
        }

        if (elementType == null) {
            elementType = PredefinedGenericTypes.ANY_TYPE;
        }

        IGenericType listType = GenericTypeHelper.buildListType(elementType);
        node.setReturnTypeInfo(listType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(listType);
        return info;
    }

    @Override
    public ReturnTypeInfo processObjectExpression(ObjectExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType valueType = null;
        boolean preciseMap = true;
        if (node.getProperties() != null) {
            for (XLangASTNode prop : node.getProperties()) {
                if (prop instanceof SpreadElement) {
                    preciseMap = false;
                    ReturnTypeInfo spreadInfo = processAST(((SpreadElement) prop).getArgument(), context);
                    valueType = mergeTypes(valueType, getMapSpreadValueType(spreadInfo != null ? spreadInfo.getReturnType() : null));
                    continue;
                }

                if (!(prop instanceof PropertyAssignment)) {
                    preciseMap = false;
                    processAST(prop, context);
                    valueType = mergeTypes(valueType, PredefinedGenericTypes.ANY_TYPE);
                    continue;
                }

                PropertyAssignment assign = (PropertyAssignment) prop;
                processAST(assign.getKey(), context);
                ReturnTypeInfo propInfo = processAST(assign.getValue(), context);
                valueType = mergeTypes(valueType, propInfo != null ? propInfo.getReturnType() : null);
                if (assign.getComputed()) {
                    preciseMap = false;
                }
            }
        }

        if (valueType == null) {
            valueType = PredefinedGenericTypes.ANY_TYPE;
        }

        if (!preciseMap) {
            valueType = mergeTypes(valueType, PredefinedGenericTypes.ANY_TYPE);
        }

        IGenericType mapType = GenericTypeHelper.buildMapType(valueType);
        node.setReturnTypeInfo(mapType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(mapType);
        return info;
    }

    @Override
    public ReturnTypeInfo processPropertyAssignment(PropertyAssignment node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getKey(), context);
        return processAST(node.getValue(), context);
    }

    @Override
    public ReturnTypeInfo processParameterDeclaration(ParameterDeclaration node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType paramType = getDeclaredType(node.getType());
        ReturnTypeInfo initInfo = null;
        if (node.getInitializer() != null) {
            initInfo = processAST(node.getInitializer(), context);
        }
        if (paramType == null && initInfo != null) {
            paramType = initInfo.getReturnType();
        }
        if (paramType == null) {
            paramType = PredefinedGenericTypes.ANY_TYPE;
        }

        if (node.getName() instanceof Identifier) {
            Identifier id = (Identifier) node.getName();
            context.setVariableType(id.getName(), paramType);
            id.setReturnTypeInfo(paramType);
        }

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(paramType);
        return info;
    }

    @Override
    public ReturnTypeInfo processFunctionDeclaration(FunctionDeclaration node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType functionType = inferFunctionType(node, context);
        node.setReturnTypeInfo(functionType);
        if (node.getName() != null) {
            context.setVariableType(node.getName().getName(), functionType);
            node.getName().setReturnTypeInfo(functionType);
        }

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(functionType);
        return info;
    }

    @Override
    public ReturnTypeInfo processArrowFunctionExpression(ArrowFunctionExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType functionType = inferFunctionType(node, context);
        node.setReturnTypeInfo(functionType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(functionType);
        return info;
    }

    @Override
    public ReturnTypeInfo processUnaryExpression(UnaryExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo argInfo = processAST(node.getArgument(), context);
        IGenericType argType = argInfo != null ? argInfo.getReturnType() : null;
        IGenericType resultType = inferUnaryType(node.getOperator(), argType);
        node.setReturnTypeInfo(resultType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processUpdateExpression(UpdateExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo argInfo = processAST(node.getArgument(), context);
        IGenericType currentType = argInfo != null ? argInfo.getReturnType() : null;
        IGenericType resultType = inferUpdateType(currentType);

        if (node.getArgument() instanceof Identifier) {
            Identifier id = (Identifier) node.getArgument();
            context.setVariableType(id.getName(), resultType);
            id.setReturnTypeInfo(resultType);
        }

        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
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

    private IGenericType inferUnaryType(XLangOperator op, IGenericType argType) {
        if (op == XLangOperator.NOT) {
            return PredefinedGenericTypes.BOOLEAN_TYPE;
        }
        if (op == XLangOperator.ADD || op == XLangOperator.MINUS || op == XLangOperator.BIT_NOT) {
            if (argType != null && argType.isNumericType()) {
                return argType;
            }
            return PredefinedGenericTypes.NUMBER_TYPE;
        }
        return argType != null ? argType : PredefinedGenericTypes.ANY_TYPE;
    }

    private IGenericType inferUpdateType(IGenericType argType) {
        if (argType != null && argType.isNumericType()) {
            return argType;
        }
        return PredefinedGenericTypes.NUMBER_TYPE;
    }

    @Override
    public ReturnTypeInfo processInExpression(InExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getLeft(), context);
        processAST(node.getRight(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.BOOLEAN_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processExpressionStatement(ExpressionStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getExpression(), context);
        return null;
    }

    @Override
    public ReturnTypeInfo processAssignmentExpression(AssignmentExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo rightInfo = processAST(node.getRight(), context);
        IGenericType rightType = rightInfo != null ? rightInfo.getReturnType() : PredefinedGenericTypes.ANY_TYPE;
        IGenericType resultType = rightType;

        Expression left = node.getLeft();
        if (left instanceof Identifier) {
            Identifier id = (Identifier) left;
            IGenericType currentType = context.getVariableType(id.getName());
            if (node.getOperator() != XLangOperator.ASSIGN && currentType != null) {
                resultType = inferBinaryType(getBinaryOperatorForAssignment(node.getOperator()), currentType, rightType);
            }
            if (resultType == null) {
                resultType = PredefinedGenericTypes.ANY_TYPE;
            }
            context.setVariableType(id.getName(), resultType);
            id.setReturnTypeInfo(resultType);
        } else if (left instanceof MemberExpression) {
            processMemberExpression((MemberExpression) left, context);
        } else {
            processAST(left, context);
        }

        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
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
        XLangASTNode id = node.getId();
        if (id instanceof Identifier) {
            Identifier idNode = (Identifier) id;
            context.setVariableType(idNode.getName(), varType);
            idNode.setReturnTypeInfo(varType);
        } else {
            bindPatternType(id, varType, context);
        }

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(varType);
        return union(info, initInfo);
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
        if (context == null)
            return null;

        if (node.getArguments() != null) {
            for (Expression arg : node.getArguments()) {
                processAST(arg, context);
            }
        }

        IGenericType resultType = getDeclaredType(node.getCallee());
        if (resultType == null) {
            resultType = PredefinedGenericTypes.ANY_TYPE;
        }

        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processSpreadElement(SpreadElement node, TypeInferenceState context) {
        if (context == null)
            return null;

        return processAST(node.getArgument(), context);
    }

    @Override
    public ReturnTypeInfo processSequenceExpression(SequenceExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo result = null;
        if (node.getExpressions() != null) {
            for (Expression expr : node.getExpressions()) {
                result = processAST(expr, context);
            }
        }

        IGenericType resultType = result != null ? result.getReturnType() : PredefinedGenericTypes.ANY_TYPE;
        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processConcatExpression(ConcatExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        if (node.getExpressions() != null) {
            for (Expression expr : node.getExpressions()) {
                processAST(expr, context);
            }
        }

        node.setReturnTypeInfo(PredefinedGenericTypes.STRING_TYPE);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.STRING_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processBraceExpression(BraceExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo exprInfo = processAST(node.getExpr(), context);
        IGenericType resultType = exprInfo != null ? exprInfo.getReturnType() : PredefinedGenericTypes.ANY_TYPE;
        node.setReturnTypeInfo(resultType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processObjectBinding(ObjectBinding node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo result = null;
        if (node.getProperties() != null) {
            for (PropertyBinding property : node.getProperties()) {
                result = union(result, processAST(property, context));
            }
        }
        result = union(result, processAST(node.getRestBinding(), context));
        return result;
    }

    @Override
    public ReturnTypeInfo processPropertyBinding(PropertyBinding node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo initInfo = processAST(node.getInitializer(), context);
        IGenericType type = resolveBindingType(node.getIdentifier(), initInfo, context);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(type);
        return union(info, initInfo);
    }

    @Override
    public ReturnTypeInfo processRestBinding(RestBinding node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo initInfo = processAST(node.getInitializer(), context);
        IGenericType type = resolveBindingType(node.getIdentifier(), initInfo, context);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(type);
        return union(info, initInfo);
    }

    @Override
    public ReturnTypeInfo processArrayBinding(ArrayBinding node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo result = null;
        if (node.getElements() != null) {
            for (ArrayElementBinding element : node.getElements()) {
                result = union(result, processAST(element, context));
            }
        }
        result = union(result, processAST(node.getRestBinding(), context));
        return result;
    }

    @Override
    public ReturnTypeInfo processArrayElementBinding(ArrayElementBinding node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo initInfo = processAST(node.getInitializer(), context);
        IGenericType type = resolveBindingType(node.getIdentifier(), initInfo, context);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(type);
        return union(info, initInfo);
    }

    @Override
    public ReturnTypeInfo processImportDefaultSpecifier(ImportDefaultSpecifier node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType type = ensureImportedLocalType(node.getLocal(), context);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(type);
        return info;
    }

    @Override
    public ReturnTypeInfo processImportNamespaceSpecifier(ImportNamespaceSpecifier node, TypeInferenceState context) {
        if (context == null)
            return null;

        IGenericType type = ensureImportedLocalType(node.getLocal(), context);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(type);
        return info;
    }

    @Override
    public ReturnTypeInfo processAwaitExpression(AwaitExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        ReturnTypeInfo argInfo = processAST(node.getArgument(), context);
        IGenericType resultType = argInfo != null ? argInfo.getReturnType() : PredefinedGenericTypes.ANY_TYPE;
        node.setReturnTypeInfo(resultType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processUsingStatement(UsingStatement node, TypeInferenceState context) {
        if (context == null)
            return null;

        TypeInferenceState usingState = context.newChild();
        ReturnTypeInfo varsInfo = processAST(node.getVars(), usingState);
        ReturnTypeInfo bodyInfo = processAST(node.getBody(), usingState);
        return union(varsInfo, bodyInfo);
    }

    @Override
    public ReturnTypeInfo processTextOutputExpression(TextOutputExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        node.setReturnTypeInfo(PredefinedGenericTypes.VOID_TYPE);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processEscapeOutputExpression(EscapeOutputExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getText(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.VOID_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processCollectOutputExpression(CollectOutputExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getBody(), context);

        IGenericType resultType = inferCollectOutputType(node.getOutputMode());
        node.setReturnTypeInfo(resultType);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    @Override
    public ReturnTypeInfo processCompareOpExpression(CompareOpExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getLeft(), context);
        processAST(node.getRight(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.BOOLEAN_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processAssertOpExpression(AssertOpExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getValue(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.BOOLEAN_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processBetweenOpExpression(BetweenOpExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getValue(), context);
        processAST(node.getMin(), context);
        processAST(node.getMax(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.BOOLEAN_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processGenNodeExpression(GenNodeExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getTagName(), context);
        processAST(node.getExtAttrs(), context);
        if (node.getAttrs() != null) {
            for (GenNodeAttrExpression attr : node.getAttrs()) {
                processAST(attr, context);
            }
        }
        processAST(node.getBody(), context);

        node.setReturnTypeInfo(PredefinedGenericTypes.VOID_TYPE);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processGenNodeAttrExpression(GenNodeAttrExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getValue(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.VOID_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.VOID_TYPE);
        return info;
    }


    @Override
    public ReturnTypeInfo processTypeOfExpression(TypeOfExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getArgument(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.STRING_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.STRING_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processInstanceOfExpression(InstanceOfExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getValue(), context);
        node.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);

        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(PredefinedGenericTypes.BOOLEAN_TYPE);
        return info;
    }

    @Override
    public ReturnTypeInfo processCastExpression(CastExpression node, TypeInferenceState context) {
        if (context == null)
            return null;

        processAST(node.getValue(), context);
        IGenericType resultType = getDeclaredType(node.getAsType());
        if (resultType == null) {
            resultType = PredefinedGenericTypes.ANY_TYPE;
        }

        node.setReturnTypeInfo(resultType);
        ReturnTypeInfo info = new ReturnTypeInfo();
        info.setReturnType(resultType);
        return info;
    }

    private ReturnTypeInfo processSequentialNodes(List<? extends XLangASTNode> nodes, TypeInferenceState context) {
        if (context == null || nodes == null) {
            return null;
        }

        ReturnTypeInfo result = null;
        for (XLangASTNode child : nodes) {
            ReturnTypeInfo info = processAST(child, context);
            result = union(result, info);
        }
        return result;
    }

    private IGenericType inferCollectOutputType(io.nop.xlang.ast.XLangOutputMode outputMode) {
        if (outputMode == null) {
            return PredefinedGenericTypes.STRING_TYPE;
        }
        switch (outputMode) {
            case node:
                return ReflectionManager.instance().buildRawType(XNode.class);
            case sql:
                return ReflectionManager.instance().buildRawType(SQL.class);
            case xjson:
                return PredefinedGenericTypes.ANY_TYPE;
            default:
                return PredefinedGenericTypes.STRING_TYPE;
        }
    }

    private ReturnTypeInfo processLoopBody(Expression body, TypeInferenceState loopState) {
        if (body == null || loopState == null) {
            return null;
        }
        return processAST(body, loopState);
    }

    private void mergeConditionalVariableTypes(TypeInferenceState target, TypeInferenceState branch1,
                                               TypeInferenceState branch2, boolean hasElseBranch) {
        if (target == null || branch1 == null) {
            return;
        }

        Map<String, IGenericType> baseTypes = target.getAllVariableTypes();
        Set<String> names = new HashSet<>();
        names.addAll(branch1.getLocalVariableTypes().keySet());
        if (branch2 != null) {
            names.addAll(branch2.getLocalVariableTypes().keySet());
        }

        for (String name : names) {
            IGenericType baseType = baseTypes.get(name);
            IGenericType type1 = branch1.hasLocalVariable(name) ? branch1.getLocalVariableTypes().get(name) : baseType;
            IGenericType type2;
            if (branch2 != null) {
                type2 = branch2.hasLocalVariable(name) ? branch2.getLocalVariableTypes().get(name) : baseType;
            } else if (!hasElseBranch) {
                type2 = baseType;
            } else {
                type2 = null;
            }

            IGenericType mergedType = mergeTypes(type1, type2);
            target.setVariableType(name, mergedType != null ? mergedType : PredefinedGenericTypes.ANY_TYPE);
        }
    }

    private XLangOperator getBinaryOperatorForAssignment(XLangOperator op) {
        if (op == null) {
            return null;
        }
        switch (op) {
            case SELF_ASSIGN_ADD:
                return XLangOperator.ADD;
            case SELF_ASSIGN_MINUS:
                return XLangOperator.MINUS;
            case SELF_ASSIGN_MULTI:
                return XLangOperator.MULTIPLY;
            case SELF_ASSIGN_DIV:
                return XLangOperator.DIVIDE;
            case SELF_ASSIGN_MOD:
                return XLangOperator.MOD;
            default:
                return op;
        }
    }

    private IGenericType getSpreadElementType(IGenericType spreadType) {
        if (spreadType == null) {
            return PredefinedGenericTypes.ANY_TYPE;
        }
        if (spreadType.isArray() || spreadType.isListLike()) {
            return spreadType.getComponentType();
        }
        return PredefinedGenericTypes.ANY_TYPE;
    }

    private IGenericType getMapSpreadValueType(IGenericType spreadType) {
        if (spreadType == null) {
            return PredefinedGenericTypes.ANY_TYPE;
        }
        if (spreadType.isMapLike()) {
            IGenericType valueType = spreadType.getMapValueType();
            return valueType != null ? valueType : PredefinedGenericTypes.ANY_TYPE;
        }
        return PredefinedGenericTypes.ANY_TYPE;
    }

    private IGenericType getDeclaredType(io.nop.xlang.ast.NamedTypeNode typeNode) {
        if (typeNode == null) {
            return null;
        }
        return typeNode.getTypeInfo();
    }

    private IGenericType inferFunctionType(FunctionExpression node, TypeInferenceState outerContext) {
        TypeInferenceState fnState = outerContext.newChild();
        List<IGenericType> argTypes = new ArrayList<>();
        List<String> argNames = new ArrayList<>();

        if (node.getParams() != null) {
            for (ParameterDeclaration param : node.getParams()) {
                IGenericType paramType = getDeclaredType(param.getType());
                if (paramType == null) {
                    paramType = PredefinedGenericTypes.ANY_TYPE;
                }
                if (param.getName() instanceof Identifier) {
                    Identifier id = (Identifier) param.getName();
                    fnState.setVariableType(id.getName(), paramType);
                    id.setReturnTypeInfo(paramType);
                    argNames.add(id.getName());
                } else {
                    argNames.add("arg" + argNames.size());
                }
                argTypes.add(paramType);
            }
        }

        IGenericType declaredReturnType = null;
        if (node instanceof FunctionDeclaration) {
            declaredReturnType = getDeclaredType(((FunctionDeclaration) node).getReturnType());
        } else if (node instanceof ArrowFunctionExpression) {
            declaredReturnType = getDeclaredType(((ArrowFunctionExpression) node).getReturnType());
        }

        fnState.setCurrentReturnType(declaredReturnType);
        ReturnTypeInfo bodyInfo = processAST(node.getBody(), fnState);

        IGenericType returnType = declaredReturnType;
        if (returnType == null) {
            returnType = inferFunctionBodyReturnType(node, bodyInfo);
        }
        if (returnType == null) {
            returnType = PredefinedGenericTypes.ANY_TYPE;
        }

        return new GenericFunctionTypeImpl(argNames, argTypes, returnType);
    }

    private IGenericType inferFunctionBodyReturnType(FunctionExpression node, ReturnTypeInfo bodyInfo) {
        if (node instanceof ArrowFunctionExpression && ((ArrowFunctionExpression) node).isExpression()) {
            return node.getBody() != null && node.getBody().getReturnTypeInfo() != null
                    ? node.getBody().getReturnTypeInfo()
                    : bodyInfo != null ? bodyInfo.getReturnType() : null;
        }
        if (bodyInfo != null && bodyInfo.getReturnType() != null) {
            return bodyInfo.getReturnType();
        }
        return PredefinedGenericTypes.VOID_TYPE;
    }

    private void bindPatternType(XLangASTNode pattern, IGenericType type, TypeInferenceState context) {
        if (pattern == null || context == null) {
            return;
        }

        switch (pattern.getASTKind()) {
            case Identifier:
                Identifier id = (Identifier) pattern;
                IGenericType resolvedType = type != null ? type : PredefinedGenericTypes.ANY_TYPE;
                context.setVariableType(id.getName(), resolvedType);
                id.setReturnTypeInfo(resolvedType);
                break;
            case ArrayBinding:
                bindArrayPattern((ArrayBinding) pattern, type, context);
                break;
            case ObjectBinding:
                bindObjectPattern((ObjectBinding) pattern, type, context);
                break;
            default:
                break;
        }
    }

    private void bindArrayPattern(ArrayBinding pattern, IGenericType type, TypeInferenceState context) {
        IGenericType elementType = getSpreadElementType(type);
        if (pattern.getElements() != null) {
            for (ArrayElementBinding element : pattern.getElements()) {
                bindPatternType(element.getIdentifier(), elementType, context);
                if (element.getInitializer() != null) {
                    processAST(element.getInitializer(), context);
                }
            }
        }
        if (pattern.getRestBinding() != null) {
            bindPatternType(pattern.getRestBinding().getIdentifier(), GenericTypeHelper.buildListType(elementType), context);
        }
    }

    private void bindObjectPattern(ObjectBinding pattern, IGenericType type, TypeInferenceState context) {
        IGenericType valueType = getMapSpreadValueType(type);
        if (pattern.getProperties() != null) {
            for (PropertyBinding prop : pattern.getProperties()) {
                bindPatternType(prop.getIdentifier(), valueType, context);
                if (prop.getInitializer() != null) {
                    processAST(prop.getInitializer(), context);
                }
            }
        }
        if (pattern.getRestBinding() != null) {
            bindPatternType(pattern.getRestBinding().getIdentifier(), GenericTypeHelper.buildMapType(valueType), context);
        }
    }

    private void bindLoopVariable(Expression left, IGenericType itemType, TypeInferenceState context) {
        if (left == null || context == null) {
            return;
        }

        if (left instanceof Identifier) {
            Identifier id = (Identifier) left;
            context.setVariableType(id.getName(), itemType);
            id.setReturnTypeInfo(itemType);
            return;
        }

        processAST(left, context);
    }

    private IGenericType ensureBindingIdentifierType(Identifier id, TypeInferenceState context) {
        IGenericType type = PredefinedGenericTypes.ANY_TYPE;
        if (id == null || context == null) {
            return type;
        }

        IGenericType existingType = context.getVariableType(id.getName());
        if (existingType != null) {
            type = existingType;
        }
        context.setVariableType(id.getName(), type);
        id.setReturnTypeInfo(type);
        return type;
    }

    private IGenericType resolveBindingType(Identifier id, ReturnTypeInfo initInfo, TypeInferenceState context) {
        IGenericType initType = initInfo != null ? initInfo.getReturnType() : null;
        IGenericType existingType = null;
        if (id != null && context != null) {
            existingType = context.getVariableType(id.getName());
        }

        IGenericType resolvedType;
        if (existingType == null || existingType.isAnyType()) {
            resolvedType = initType != null ? initType : PredefinedGenericTypes.ANY_TYPE;
        } else if (initType == null || initType.isAnyType()) {
            resolvedType = existingType;
        } else {
            resolvedType = mergeTypes(existingType, initType);
        }

        if (id != null && context != null) {
            context.setVariableType(id.getName(), resolvedType);
            id.setReturnTypeInfo(resolvedType);
        }
        return resolvedType;
    }

    private IGenericType ensureImportedLocalType(Identifier id, TypeInferenceState context) {
        if (id == null || context == null) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        IGenericType type = context.getVariableType(id.getName());
        if (type == null) {
            type = PredefinedGenericTypes.ANY_TYPE;
            context.setVariableType(id.getName(), type);
        }
        id.setReturnTypeInfo(type);
        return type;
    }

}
