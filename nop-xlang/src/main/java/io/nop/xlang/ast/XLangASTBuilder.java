/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class XLangASTBuilder {
    public static <T extends XLangASTNode> T deepClone(T node) {
        if (node == null)
            return null;
        return (T) node.deepClone();
    }

    public static VariableDeclaration let(SourceLocation loc, XLangASTNode id, NamedTypeNode type, Expression init) {
        return VariableDeclaration.valueOf(loc, VariableKind.LET, id, type, init);
    }

    public static VariableDeclaration scopeLet(SourceLocation loc, XLangASTNode id, NamedTypeNode type,
                                               Expression init) {
        return VariableDeclaration.valueOf(loc, VariableKind.LET, id, type, init);
    }

    public static VariableDeclaration constDecl(SourceLocation loc, XLangASTNode id, NamedTypeNode type,
                                                Expression init) {
        return VariableDeclaration.valueOf(loc, VariableKind.CONST, id, type, init);
    }

    public static Literal literal(SourceLocation loc, Object value) {
        return Literal.valueOf(loc, value);
    }

    public static Identifier identifier(SourceLocation loc, String name) {
        return Identifier.valueOf(loc, name);
    }

    public static CallExpression objectCall(SourceLocation loc, Expression obj, Expression method,
                                            List<Expression> args) {
        return call(loc, member(loc, obj, method, false), args);
    }

    public static CallExpression objectCall(SourceLocation loc, Expression obj, String method, List<Expression> args) {
        return objectCall(loc, obj, identifier(loc, method), args);
    }

    public static MemberExpression member(SourceLocation loc, Expression obj, Expression property, boolean computed) {
        return MemberExpression.valueOf(loc, obj, property, computed);
    }

    public static CallExpression call(SourceLocation loc, Expression callee, List<Expression> args) {
        return CallExpression.valueOf(loc, callee, args);
    }

    public static NewExpression newObject(SourceLocation loc, NamedTypeNode type, List<Expression> args) {
        return NewExpression.valueOf(loc, type, args);
    }

    public static TypeNameNode typeName(SourceLocation loc, Type type) {
        return TypeNameNode.fromType(loc, type);
    }

    public static TypeNameNode typeName(SourceLocation loc, String typeName) {
        return TypeNameNode.valueOf(loc, typeName);
    }

    public static BlockStatement blockStatement(SourceLocation loc, List<Expression> body) {
        return BlockStatement.valueOf(loc, body);
    }

    public static ForStatement forStatement(SourceLocation loc, Expression init, Expression test, Expression update,
                                            Expression body) {
        return ForStatement.valueOf(loc, init, test, update, body);
    }

    public static WhileStatement whileStatement(SourceLocation loc, Expression test, Expression body) {
        return WhileStatement.valueOf(loc, test, body);
    }

    public static IfStatement ifStatement(SourceLocation loc, Expression test, Expression consequence,
                                          Expression alternate) {
        return IfStatement.valueOf(loc, test, consequence, alternate);
    }

    public static TryStatement tryStatement(SourceLocation loc, Expression block, CatchClause handlers,
                                            Expression finalizer) {
        return TryStatement.valueOf(loc, block, handlers, finalizer);
    }

    public static CatchClause catchClause(SourceLocation loc, Identifier name, NamedTypeNode type, Expression body) {
        return CatchClause.valueOf(loc, name, type, body);
    }

    public static void initConditional(IConditionalExpression expr, List<Expression> exprs, int index) {
        int size = exprs.size();
        if (index >= size)
            return;

        expr.setConsequent(exprs.get(index));
        index++;

        if (size - 1 == index) {
            expr.setAlternate(exprs.get(index));
        } else if (index < size - 1) {
            Expression next = exprs.get(index);
            IConditionalExpression right = expr.createInstance();
            right.setLocation(next.getLocation());
            right.setTest(next);
            initConditional(right, exprs, index + 1);
            expr.setAlternate((Expression) right);
        }
    }

    public static Expression not(SourceLocation loc, Expression expr) {
        if (expr == null)
            return Literal.booleanValue(loc, true);
        return UnaryExpression.valueOf(loc, XLangOperator.NOT, expr);
    }

    public static Expression assign(SourceLocation loc, Identifier var, Expression expr) {
        if (expr == null)
            expr = Literal.nullValue(loc);
        return AssignmentExpression.valueOf(loc, var, XLangOperator.ASSIGN, expr);
    }

    public static Expression varDecl(SourceLocation loc, Identifier var, Expression expr) {
        if (expr == null)
            expr = Literal.nullValue(loc);
        VariableDeclaration decl = new VariableDeclaration();
        decl.setKind(VariableKind.VAR);
        decl.setLocation(loc);
        VariableDeclarator declarator = new VariableDeclarator();
        declarator.setLocation(loc);
        declarator.setId(var);
        declarator.setInit(expr);
        decl.setDeclarators(Arrays.asList(declarator));

        return decl;
    }

    public static Expression ifStatement(SourceLocation loc, Expression test, Expression consequence) {
        return IfStatement.valueOf(loc, test, consequence, null);
    }

    public static ImportAsDeclaration importLib(SourceLocation loc, Literal lib, Identifier local) {
        return ImportAsDeclaration.valueOf(loc, lib, local);
    }

    public static ImportAsDeclaration importClass(SourceLocation loc, QualifiedName className, Identifier local) {
        return ImportAsDeclaration.valueOf(loc, className, local);
    }

    public static Expression prependAll(Expression expr, List<? extends Expression> exprs) {
        if (expr instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) expr;
            block.makeBody().addAll(0, exprs);
            return block;
        } else {
            List<Expression> all = new ArrayList<>(exprs.size() + 1);
            all.addAll(exprs);
            if (expr != null)
                all.add(expr);
            return SequenceExpression.valueOf(exprs.get(0).getLocation(), all);
        }
    }

    public static NamedTypeNode buildTypeNode(SourceLocation loc, IGenericType type) {
        if (type == null)
            return null;
        TypeNameNode node = new TypeNameNode();
        node.setLocation(loc);
        node.setTypeInfo(type);
        node.setTypeName(type.getTypeName());
        return node;
    }

    public static List<ParameterDeclaration> buildParams(IFunctionType funcType) {
        List<ParameterDeclaration> params = new ArrayList<>(funcType.getFuncArgCount());
        for (int i = 0, n = funcType.getFuncArgCount(); i < n; i++) {
            ParameterDeclaration decl = new ParameterDeclaration();
            decl.setName(Identifier.valueOf(null, funcType.getFuncArgNames().get(i)));
            decl.setType(XLangASTBuilder.buildTypeNode(null, funcType.getFuncArgTypes().get(i)));
            params.add(decl);
        }
        return params;
    }

    public static ParameterDeclaration paramDecl(SourceLocation loc, String id) {
        ParameterDeclaration decl = new ParameterDeclaration();
        decl.setLocation(loc);
        decl.setName(Identifier.valueOf(loc, id));
        return decl;
    }

    public static ParameterDeclaration paramDecl(SourceLocation loc, String id, IGenericType type) {
        ParameterDeclaration decl = paramDecl(loc, id);
        if (type != null) {
            decl.setType(buildTypeNode(loc, type));
        }
        return decl;
    }

    public static ObjectBinding objectBinding(SourceLocation loc, Map<String, String> map) {
        ObjectBinding binding = new ObjectBinding();
        binding.setLocation(loc);

        List<PropertyBinding> props = new ArrayList<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            PropertyBinding prop = new PropertyBinding();
            prop.setLocation(loc);
            prop.setPropName(entry.getKey());
            prop.setIdentifier(Identifier.valueOf(loc, entry.getValue()));
            props.add(prop);
        }
        binding.setProperties(props);
        return binding;
    }

    public static ParameterDeclaration paramDecl(ObjectBinding binding) {
        ParameterDeclaration decl = new ParameterDeclaration();
        decl.setLocation(binding.getLocation());
        decl.setName(binding);
        return decl;
    }

    public static <T extends XLangASTNode> List<T> cloneNodes(List<T> nodes) {
        if (nodes == null)
            return null;
        List<T> ret = new ArrayList<>(nodes.size());
        for (T node : nodes) {
            ret.add((T) node.deepClone());
        }
        return ret;
    }

    public static BlockStatement toBlock(SourceLocation loc, Expression expr) {
        if (expr == null)
            return null;
        if (expr instanceof BlockStatement)
            return (BlockStatement) expr;
        return BlockStatement.valueOf(loc, Collections.singletonList(expr));
    }

    public static Expression buildPropExpr(SourceLocation loc, String propName) {
        int pos = propName.indexOf('.');
        if (pos < 0)
            return Identifier.valueOf(loc, propName);

        Expression id = Identifier.valueOf(loc, propName.substring(0, pos));

        Expression prop = buildPropExpr(loc, propName.substring(pos + 1));
        return MemberExpression.valueOf(loc, id, prop, false, true);
    }
}