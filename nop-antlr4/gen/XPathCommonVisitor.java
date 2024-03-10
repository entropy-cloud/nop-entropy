/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
// Generated from /home/u/sources/platform/entropy-cloud/nop-antlr4/nop-antlr4-xpath/src/main/antlr4/imports/XPathCommon.g4 by ANTLR 4.9

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link XPathCommon}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 *            operations with no return type.
 */
public interface XPathCommonVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link XPathCommon#qualifiedType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitQualifiedType(XPathCommon.QualifiedTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#qualifiedName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitQualifiedName(XPathCommon.QualifiedNameContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#propertyName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPropertyName(XPathCommon.PropertyNameContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#identifier}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdentifier(XPathCommon.IdentifierContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#identifierOrKeyword}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdentifierOrKeyword(XPathCommon.IdentifierOrKeywordContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#reservedWord}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitReservedWord(XPathCommon.ReservedWordContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#keyword}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitKeyword(XPathCommon.KeywordContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#literal}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLiteral(XPathCommon.LiteralContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathCommon#numericLiteral}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNumericLiteral(XPathCommon.NumericLiteralContext ctx);
}