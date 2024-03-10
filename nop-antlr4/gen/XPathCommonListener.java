/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
// Generated from /home/u/sources/platform/entropy-cloud/nop-antlr4/nop-antlr4-xpath/src/main/antlr4/imports/XPathCommon.g4 by ANTLR 4.9

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link XPathCommon}.
 */
public interface XPathCommonListener extends ParseTreeListener {
    /**
     * Enter a parse tree produced by {@link XPathCommon#qualifiedType}.
     *
     * @param ctx the parse tree
     */
    void enterQualifiedType(XPathCommon.QualifiedTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#qualifiedType}.
     *
     * @param ctx the parse tree
     */
    void exitQualifiedType(XPathCommon.QualifiedTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#qualifiedName}.
     *
     * @param ctx the parse tree
     */
    void enterQualifiedName(XPathCommon.QualifiedNameContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#qualifiedName}.
     *
     * @param ctx the parse tree
     */
    void exitQualifiedName(XPathCommon.QualifiedNameContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#propertyName}.
     *
     * @param ctx the parse tree
     */
    void enterPropertyName(XPathCommon.PropertyNameContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#propertyName}.
     *
     * @param ctx the parse tree
     */
    void exitPropertyName(XPathCommon.PropertyNameContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#identifier}.
     *
     * @param ctx the parse tree
     */
    void enterIdentifier(XPathCommon.IdentifierContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#identifier}.
     *
     * @param ctx the parse tree
     */
    void exitIdentifier(XPathCommon.IdentifierContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#identifierOrKeyword}.
     *
     * @param ctx the parse tree
     */
    void enterIdentifierOrKeyword(XPathCommon.IdentifierOrKeywordContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#identifierOrKeyword}.
     *
     * @param ctx the parse tree
     */
    void exitIdentifierOrKeyword(XPathCommon.IdentifierOrKeywordContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#reservedWord}.
     *
     * @param ctx the parse tree
     */
    void enterReservedWord(XPathCommon.ReservedWordContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#reservedWord}.
     *
     * @param ctx the parse tree
     */
    void exitReservedWord(XPathCommon.ReservedWordContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#keyword}.
     *
     * @param ctx the parse tree
     */
    void enterKeyword(XPathCommon.KeywordContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#keyword}.
     *
     * @param ctx the parse tree
     */
    void exitKeyword(XPathCommon.KeywordContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#literal}.
     *
     * @param ctx the parse tree
     */
    void enterLiteral(XPathCommon.LiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#literal}.
     *
     * @param ctx the parse tree
     */
    void exitLiteral(XPathCommon.LiteralContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathCommon#numericLiteral}.
     *
     * @param ctx the parse tree
     */
    void enterNumericLiteral(XPathCommon.NumericLiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathCommon#numericLiteral}.
     *
     * @param ctx the parse tree
     */
    void exitNumericLiteral(XPathCommon.NumericLiteralContext ctx);
}