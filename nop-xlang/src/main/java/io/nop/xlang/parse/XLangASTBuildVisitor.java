/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.parse.antlr.XLangParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import static io.nop.antlr4.common.ParseTreeHelper.loc;
import static io.nop.antlr4.common.ParseTreeHelper.terminalNode;
import static io.nop.antlr4.common.ParseTreeHelper.text;
import static io.nop.antlr4.common.ParseTreeHelper.token;
import static io.nop.xlang.XLangErrors.ERR_XLANG_INVALID_PARSE_TREE;

@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class XLangASTBuildVisitor extends _XLangASTBuildVisitor {

    /**
     * rules: ChainExpression
     */
    public boolean ChainExpression_optional(org.antlr.v4.runtime.Token token) {
        return isTokenNot(token);
    }

    private boolean isTokenNot(Token token) {
        return token != null && token.getType() == XLangParser.Not;
    }

    /**
     * rules: functionArgTypeDef
     */
    public boolean FunctionArgTypeDef_optional(org.antlr.v4.runtime.Token token) {
        return isTokenNot(token);
    }

    /**
     * rules: propertyTypeDef
     */
    public boolean PropertyTypeDef_optional(org.antlr.v4.runtime.Token token) {
        return isTokenNot(token);
    }

    /**
     * rules: propertyTypeDef
     */
    public boolean PropertyTypeDef_readonly(org.antlr.v4.runtime.Token token) {
        return token != null && token.getType() == XLangParser.ReadOnly;
    }

    /**
     * rules: VariableDeclaration_for,variableDeclaration
     */
    public io.nop.xlang.ast.VariableKind VariableDeclaration_kind(ParseTree node) {
        return XLangParseHelper.variableKind(token(node));
    }

    /**
     * rules: variableDeclaration_const
     */
    public io.nop.xlang.ast.VariableKind VariableDeclaration_kind(org.antlr.v4.runtime.Token token) {
        return XLangParseHelper.variableKind(token);
    }

    /**
     * rules: assignmentExpression
     */
    public io.nop.xlang.ast.XLangOperator AssignmentExpression_operator(ParseTree node) {
        return XLangParseHelper.operator(token(node));
    }

    /**
     * rules: assignmentExpression_init
     */
    public io.nop.xlang.ast.XLangOperator AssignmentExpression_operator(org.antlr.v4.runtime.Token token) {
        return XLangParseHelper.operator(token);
    }

    /**
     * rules: BinaryExpression
     */
    public io.nop.xlang.ast.XLangOperator BinaryExpression_operator(org.antlr.v4.runtime.Token token) {
        return XLangParseHelper.operator(token);
    }

    /**
     * rules: UnaryExpression
     */
    public io.nop.xlang.ast.XLangOperator UnaryExpression_operator(org.antlr.v4.runtime.Token token) {
        return XLangParseHelper.operator(token);
    }

    /**
     * rules: UpdateExpression
     */
    public io.nop.xlang.ast.XLangOperator UpdateExpression_operator(org.antlr.v4.runtime.Token token) {
        return XLangParseHelper.operator(token);
    }

    /**
     * rules: literal,literal_numeric,literal_string
     */
    public java.lang.Object Literal_value(ParseTree node) {
        return XLangParseHelper.literalValue(terminalNode(node));
    }

    /**
     * rules: templateStringLiteral
     */
    public java.lang.Object TemplateStringLiteral_value(org.antlr.v4.runtime.Token token) {
        return XLangParseHelper.templateStringLiteralValue(token);
    }

    /**
     * rules: identifier,identifier_ex
     */
    public java.lang.String Identifier_name(ParseTree node) {
        return text(node);
    }

    /**
     * rules: parameterizedTypeNode
     */
    public java.lang.String ParameterizedTypeNode_typeName(ParseTree node) {
        return text(node);
    }

    /**
     * rules: PropertyBinding_full
     */
    public java.lang.String PropertyBinding_propName(ParseTree node) {
        return text(node);
    }

    /**
     * rules: propertyTypeDef
     */
    public java.lang.String PropertyTypeDef_name(ParseTree node) {
        return text(node);
    }

    /**
     * rules: qualifiedName
     */
    public java.lang.String QualifiedName_name(ParseTree node) {
        return text(node);
    }

    /**
     * rules: TypeNameNode_named,typeNameNode_predefined
     */
    public java.lang.String TypeNameNode_typeName(ParseTree node) {
        if (node instanceof TerminalNode)
            return text(node);
        if (node instanceof XLangParser.QualifiedName_Context) {
            XLangParser.QualifiedName_Context ctx = (XLangParser.QualifiedName_Context) node;
            QualifiedName next = buildQualifiedName_(ctx);
            return next.getFullName();
        }
        throw new NopException(ERR_XLANG_INVALID_PARSE_TREE).loc(loc((ParserRuleContext) node));
    }

    @Override
    public boolean CallExpression_optional(Token token) {
        return token.getType() == XLangParser.OptionalDot;
    }

    @Override
    public boolean MemberExpression_optional(Token token) {
        return token.getType() == XLangParser.OptionalDot;
    }
}
