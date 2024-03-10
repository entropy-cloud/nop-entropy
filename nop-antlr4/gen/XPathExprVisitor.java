/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
// Generated from /home/u/sources/platform/entropy-cloud/nop-antlr4/nop-antlr4-xpath/src/main/antlr4/imports/XPathExpr.g4 by ANTLR 4.9

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link XPathExpr}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 *            operations with no return type.
 */
public interface XPathExprVisitor<T> extends ParseTreeVisitor<T> {
    /**
     * Visit a parse tree produced by {@link XPathExpr#parameterList}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParameterList(XPathExpr.ParameterListContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#parameter}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParameter(XPathExpr.ParameterContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#identifierOrPattern}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdentifierOrPattern(XPathExpr.IdentifierOrPatternContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#namespaceName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNamespaceName(XPathExpr.NamespaceNameContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#arrayLiteral}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArrayLiteral(XPathExpr.ArrayLiteralContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#elementList}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitElementList(XPathExpr.ElementListContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#arrayElement}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArrayElement(XPathExpr.ArrayElementContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#objectLiteral}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitObjectLiteral(XPathExpr.ObjectLiteralContext ctx);

    /**
     * Visit a parse tree produced by the {@code PropertyExpressionAssignment}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPropertyExpressionAssignment(XPathExpr.PropertyExpressionAssignmentContext ctx);

    /**
     * Visit a parse tree produced by the {@code ComputedPropertyExpressionAssignment}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitComputedPropertyExpressionAssignment(XPathExpr.ComputedPropertyExpressionAssignmentContext ctx);

    /**
     * Visit a parse tree produced by the {@code PropertyShorthand}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPropertyShorthand(XPathExpr.PropertyShorthandContext ctx);

    /**
     * Visit a parse tree produced by the {@code RestParameterInObject}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitRestParameterInObject(XPathExpr.RestParameterInObjectContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#arguments}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArguments(XPathExpr.ArgumentsContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#argumentList}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArgumentList(XPathExpr.ArgumentListContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#argument}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArgument(XPathExpr.ArgumentContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#expressionSequence}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitExpressionSequence(XPathExpr.ExpressionSequenceContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#initExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInitExpression(XPathExpr.InitExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#initExpressionSequence}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInitExpressionSequence(XPathExpr.InitExpressionSequenceContext ctx);

    /**
     * Visit a parse tree produced by the {@code TemplateStringExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTemplateStringExpression(XPathExpr.TemplateStringExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code TernaryExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTernaryExpression(XPathExpr.TernaryExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code LogicalAndExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLogicalAndExpression(XPathExpr.LogicalAndExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code ChainExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitChainExpression(XPathExpr.ChainExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code SwitchExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitSwitchExpression(XPathExpr.SwitchExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code PreIncrementExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPreIncrementExpression(XPathExpr.PreIncrementExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code ObjectLiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitObjectLiteralExpression(XPathExpr.ObjectLiteralExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code InExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInExpression(XPathExpr.InExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code LogicalOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLogicalOrExpression(XPathExpr.LogicalOrExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code NotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNotExpression(XPathExpr.NotExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code PreDecreaseExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPreDecreaseExpression(XPathExpr.PreDecreaseExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code ThisExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitThisExpression(XPathExpr.ThisExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code UnaryMinusExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitUnaryMinusExpression(XPathExpr.UnaryMinusExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code PostDecreaseExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPostDecreaseExpression(XPathExpr.PostDecreaseExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code TypeofExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitTypeofExpression(XPathExpr.TypeofExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code InstanceofExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitInstanceofExpression(XPathExpr.InstanceofExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code UnaryPlusExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitUnaryPlusExpression(XPathExpr.UnaryPlusExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code EqualityExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitEqualityExpression(XPathExpr.EqualityExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code BitXOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBitXOrExpression(XPathExpr.BitXOrExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code SuperExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitSuperExpression(XPathExpr.SuperExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code MultiplicativeExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMultiplicativeExpression(XPathExpr.MultiplicativeExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code CallExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitCallExpression(XPathExpr.CallExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code BitShiftExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBitShiftExpression(XPathExpr.BitShiftExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code ParenthesizedExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitParenthesizedExpression(XPathExpr.ParenthesizedExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code IfExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIfExpression(XPathExpr.IfExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code AdditiveExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitAdditiveExpression(XPathExpr.AdditiveExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code RelationalExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitRelationalExpression(XPathExpr.RelationalExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code PostIncrementExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPostIncrementExpression(XPathExpr.PostIncrementExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code BitNotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBitNotExpression(XPathExpr.BitNotExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code LiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLiteralExpression(XPathExpr.LiteralExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code ArrayLiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitArrayLiteralExpression(XPathExpr.ArrayLiteralExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code MemberDotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMemberDotExpression(XPathExpr.MemberDotExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code MemberIndexExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitMemberIndexExpression(XPathExpr.MemberIndexExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code IdentifierExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdentifierExpression(XPathExpr.IdentifierExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code BitAndExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBitAndExpression(XPathExpr.BitAndExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code BitOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitBitOrExpression(XPathExpr.BitOrExpressionContext ctx);

    /**
     * Visit a parse tree produced by the {@code NullCoalesceExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNullCoalesceExpression(XPathExpr.NullCoalesceExpressionContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#qualifiedType}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitQualifiedType(XPathExpr.QualifiedTypeContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#qualifiedName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitQualifiedName(XPathExpr.QualifiedNameContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#propertyName}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitPropertyName(XPathExpr.PropertyNameContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#identifier}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdentifier(XPathExpr.IdentifierContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#identifierOrKeyword}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitIdentifierOrKeyword(XPathExpr.IdentifierOrKeywordContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#reservedWord}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitReservedWord(XPathExpr.ReservedWordContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#keyword}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitKeyword(XPathExpr.KeywordContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#literal}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitLiteral(XPathExpr.LiteralContext ctx);

    /**
     * Visit a parse tree produced by {@link XPathExpr#numericLiteral}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    T visitNumericLiteral(XPathExpr.NumericLiteralContext ctx);
}