/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
// Generated from /home/u/sources/platform/entropy-cloud/nop-antlr4/nop-antlr4-xpath/src/main/antlr4/imports/XPathExpr.g4 by ANTLR 4.9

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link XPathExpr}.
 */
public interface XPathExprListener extends ParseTreeListener {
    /**
     * Enter a parse tree produced by {@link XPathExpr#parameterList}.
     *
     * @param ctx the parse tree
     */
    void enterParameterList(XPathExpr.ParameterListContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#parameterList}.
     *
     * @param ctx the parse tree
     */
    void exitParameterList(XPathExpr.ParameterListContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#parameter}.
     *
     * @param ctx the parse tree
     */
    void enterParameter(XPathExpr.ParameterContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#parameter}.
     *
     * @param ctx the parse tree
     */
    void exitParameter(XPathExpr.ParameterContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#identifierOrPattern}.
     *
     * @param ctx the parse tree
     */
    void enterIdentifierOrPattern(XPathExpr.IdentifierOrPatternContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#identifierOrPattern}.
     *
     * @param ctx the parse tree
     */
    void exitIdentifierOrPattern(XPathExpr.IdentifierOrPatternContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#namespaceName}.
     *
     * @param ctx the parse tree
     */
    void enterNamespaceName(XPathExpr.NamespaceNameContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#namespaceName}.
     *
     * @param ctx the parse tree
     */
    void exitNamespaceName(XPathExpr.NamespaceNameContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#arrayLiteral}.
     *
     * @param ctx the parse tree
     */
    void enterArrayLiteral(XPathExpr.ArrayLiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#arrayLiteral}.
     *
     * @param ctx the parse tree
     */
    void exitArrayLiteral(XPathExpr.ArrayLiteralContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#elementList}.
     *
     * @param ctx the parse tree
     */
    void enterElementList(XPathExpr.ElementListContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#elementList}.
     *
     * @param ctx the parse tree
     */
    void exitElementList(XPathExpr.ElementListContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#arrayElement}.
     *
     * @param ctx the parse tree
     */
    void enterArrayElement(XPathExpr.ArrayElementContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#arrayElement}.
     *
     * @param ctx the parse tree
     */
    void exitArrayElement(XPathExpr.ArrayElementContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#objectLiteral}.
     *
     * @param ctx the parse tree
     */
    void enterObjectLiteral(XPathExpr.ObjectLiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#objectLiteral}.
     *
     * @param ctx the parse tree
     */
    void exitObjectLiteral(XPathExpr.ObjectLiteralContext ctx);

    /**
     * Enter a parse tree produced by the {@code PropertyExpressionAssignment}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void enterPropertyExpressionAssignment(XPathExpr.PropertyExpressionAssignmentContext ctx);

    /**
     * Exit a parse tree produced by the {@code PropertyExpressionAssignment}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void exitPropertyExpressionAssignment(XPathExpr.PropertyExpressionAssignmentContext ctx);

    /**
     * Enter a parse tree produced by the {@code ComputedPropertyExpressionAssignment}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void enterComputedPropertyExpressionAssignment(XPathExpr.ComputedPropertyExpressionAssignmentContext ctx);

    /**
     * Exit a parse tree produced by the {@code ComputedPropertyExpressionAssignment}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void exitComputedPropertyExpressionAssignment(XPathExpr.ComputedPropertyExpressionAssignmentContext ctx);

    /**
     * Enter a parse tree produced by the {@code PropertyShorthand}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void enterPropertyShorthand(XPathExpr.PropertyShorthandContext ctx);

    /**
     * Exit a parse tree produced by the {@code PropertyShorthand}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void exitPropertyShorthand(XPathExpr.PropertyShorthandContext ctx);

    /**
     * Enter a parse tree produced by the {@code RestParameterInObject}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void enterRestParameterInObject(XPathExpr.RestParameterInObjectContext ctx);

    /**
     * Exit a parse tree produced by the {@code RestParameterInObject}
     * labeled alternative in {@link XPathExpr#propertyAssignment}.
     *
     * @param ctx the parse tree
     */
    void exitRestParameterInObject(XPathExpr.RestParameterInObjectContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#arguments}.
     *
     * @param ctx the parse tree
     */
    void enterArguments(XPathExpr.ArgumentsContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#arguments}.
     *
     * @param ctx the parse tree
     */
    void exitArguments(XPathExpr.ArgumentsContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#argumentList}.
     *
     * @param ctx the parse tree
     */
    void enterArgumentList(XPathExpr.ArgumentListContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#argumentList}.
     *
     * @param ctx the parse tree
     */
    void exitArgumentList(XPathExpr.ArgumentListContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#argument}.
     *
     * @param ctx the parse tree
     */
    void enterArgument(XPathExpr.ArgumentContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#argument}.
     *
     * @param ctx the parse tree
     */
    void exitArgument(XPathExpr.ArgumentContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#expressionSequence}.
     *
     * @param ctx the parse tree
     */
    void enterExpressionSequence(XPathExpr.ExpressionSequenceContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#expressionSequence}.
     *
     * @param ctx the parse tree
     */
    void exitExpressionSequence(XPathExpr.ExpressionSequenceContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#initExpression}.
     *
     * @param ctx the parse tree
     */
    void enterInitExpression(XPathExpr.InitExpressionContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#initExpression}.
     *
     * @param ctx the parse tree
     */
    void exitInitExpression(XPathExpr.InitExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#initExpressionSequence}.
     *
     * @param ctx the parse tree
     */
    void enterInitExpressionSequence(XPathExpr.InitExpressionSequenceContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#initExpressionSequence}.
     *
     * @param ctx the parse tree
     */
    void exitInitExpressionSequence(XPathExpr.InitExpressionSequenceContext ctx);

    /**
     * Enter a parse tree produced by the {@code TemplateStringExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterTemplateStringExpression(XPathExpr.TemplateStringExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code TemplateStringExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitTemplateStringExpression(XPathExpr.TemplateStringExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code TernaryExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterTernaryExpression(XPathExpr.TernaryExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code TernaryExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitTernaryExpression(XPathExpr.TernaryExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code LogicalAndExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterLogicalAndExpression(XPathExpr.LogicalAndExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code LogicalAndExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitLogicalAndExpression(XPathExpr.LogicalAndExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code ChainExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterChainExpression(XPathExpr.ChainExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code ChainExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitChainExpression(XPathExpr.ChainExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code SwitchExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterSwitchExpression(XPathExpr.SwitchExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code SwitchExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitSwitchExpression(XPathExpr.SwitchExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code PreIncrementExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterPreIncrementExpression(XPathExpr.PreIncrementExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code PreIncrementExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitPreIncrementExpression(XPathExpr.PreIncrementExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code ObjectLiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterObjectLiteralExpression(XPathExpr.ObjectLiteralExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code ObjectLiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitObjectLiteralExpression(XPathExpr.ObjectLiteralExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code InExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterInExpression(XPathExpr.InExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code InExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitInExpression(XPathExpr.InExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code LogicalOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterLogicalOrExpression(XPathExpr.LogicalOrExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code LogicalOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitLogicalOrExpression(XPathExpr.LogicalOrExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code NotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterNotExpression(XPathExpr.NotExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code NotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitNotExpression(XPathExpr.NotExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code PreDecreaseExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterPreDecreaseExpression(XPathExpr.PreDecreaseExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code PreDecreaseExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitPreDecreaseExpression(XPathExpr.PreDecreaseExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code ThisExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterThisExpression(XPathExpr.ThisExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code ThisExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitThisExpression(XPathExpr.ThisExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code UnaryMinusExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterUnaryMinusExpression(XPathExpr.UnaryMinusExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code UnaryMinusExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitUnaryMinusExpression(XPathExpr.UnaryMinusExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code PostDecreaseExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterPostDecreaseExpression(XPathExpr.PostDecreaseExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code PostDecreaseExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitPostDecreaseExpression(XPathExpr.PostDecreaseExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code TypeofExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterTypeofExpression(XPathExpr.TypeofExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code TypeofExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitTypeofExpression(XPathExpr.TypeofExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code InstanceofExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterInstanceofExpression(XPathExpr.InstanceofExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code InstanceofExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitInstanceofExpression(XPathExpr.InstanceofExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code UnaryPlusExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterUnaryPlusExpression(XPathExpr.UnaryPlusExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code UnaryPlusExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitUnaryPlusExpression(XPathExpr.UnaryPlusExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code EqualityExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterEqualityExpression(XPathExpr.EqualityExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code EqualityExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitEqualityExpression(XPathExpr.EqualityExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code BitXOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterBitXOrExpression(XPathExpr.BitXOrExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code BitXOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitBitXOrExpression(XPathExpr.BitXOrExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code SuperExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterSuperExpression(XPathExpr.SuperExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code SuperExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitSuperExpression(XPathExpr.SuperExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code MultiplicativeExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterMultiplicativeExpression(XPathExpr.MultiplicativeExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code MultiplicativeExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitMultiplicativeExpression(XPathExpr.MultiplicativeExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code CallExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterCallExpression(XPathExpr.CallExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code CallExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitCallExpression(XPathExpr.CallExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code BitShiftExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterBitShiftExpression(XPathExpr.BitShiftExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code BitShiftExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitBitShiftExpression(XPathExpr.BitShiftExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code ParenthesizedExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterParenthesizedExpression(XPathExpr.ParenthesizedExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code ParenthesizedExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitParenthesizedExpression(XPathExpr.ParenthesizedExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code IfExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterIfExpression(XPathExpr.IfExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code IfExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitIfExpression(XPathExpr.IfExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code AdditiveExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterAdditiveExpression(XPathExpr.AdditiveExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code AdditiveExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitAdditiveExpression(XPathExpr.AdditiveExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code RelationalExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterRelationalExpression(XPathExpr.RelationalExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code RelationalExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitRelationalExpression(XPathExpr.RelationalExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code PostIncrementExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterPostIncrementExpression(XPathExpr.PostIncrementExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code PostIncrementExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitPostIncrementExpression(XPathExpr.PostIncrementExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code BitNotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterBitNotExpression(XPathExpr.BitNotExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code BitNotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitBitNotExpression(XPathExpr.BitNotExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code LiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterLiteralExpression(XPathExpr.LiteralExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code LiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitLiteralExpression(XPathExpr.LiteralExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code ArrayLiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterArrayLiteralExpression(XPathExpr.ArrayLiteralExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code ArrayLiteralExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitArrayLiteralExpression(XPathExpr.ArrayLiteralExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code MemberDotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterMemberDotExpression(XPathExpr.MemberDotExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code MemberDotExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitMemberDotExpression(XPathExpr.MemberDotExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code MemberIndexExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterMemberIndexExpression(XPathExpr.MemberIndexExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code MemberIndexExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitMemberIndexExpression(XPathExpr.MemberIndexExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code IdentifierExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterIdentifierExpression(XPathExpr.IdentifierExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code IdentifierExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitIdentifierExpression(XPathExpr.IdentifierExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code BitAndExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterBitAndExpression(XPathExpr.BitAndExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code BitAndExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitBitAndExpression(XPathExpr.BitAndExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code BitOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterBitOrExpression(XPathExpr.BitOrExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code BitOrExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitBitOrExpression(XPathExpr.BitOrExpressionContext ctx);

    /**
     * Enter a parse tree produced by the {@code NullCoalesceExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void enterNullCoalesceExpression(XPathExpr.NullCoalesceExpressionContext ctx);

    /**
     * Exit a parse tree produced by the {@code NullCoalesceExpression}
     * labeled alternative in {@link XPathExpr#singleExpression}.
     *
     * @param ctx the parse tree
     */
    void exitNullCoalesceExpression(XPathExpr.NullCoalesceExpressionContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#qualifiedType}.
     *
     * @param ctx the parse tree
     */
    void enterQualifiedType(XPathExpr.QualifiedTypeContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#qualifiedType}.
     *
     * @param ctx the parse tree
     */
    void exitQualifiedType(XPathExpr.QualifiedTypeContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#qualifiedName}.
     *
     * @param ctx the parse tree
     */
    void enterQualifiedName(XPathExpr.QualifiedNameContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#qualifiedName}.
     *
     * @param ctx the parse tree
     */
    void exitQualifiedName(XPathExpr.QualifiedNameContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#propertyName}.
     *
     * @param ctx the parse tree
     */
    void enterPropertyName(XPathExpr.PropertyNameContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#propertyName}.
     *
     * @param ctx the parse tree
     */
    void exitPropertyName(XPathExpr.PropertyNameContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#identifier}.
     *
     * @param ctx the parse tree
     */
    void enterIdentifier(XPathExpr.IdentifierContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#identifier}.
     *
     * @param ctx the parse tree
     */
    void exitIdentifier(XPathExpr.IdentifierContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#identifierOrKeyword}.
     *
     * @param ctx the parse tree
     */
    void enterIdentifierOrKeyword(XPathExpr.IdentifierOrKeywordContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#identifierOrKeyword}.
     *
     * @param ctx the parse tree
     */
    void exitIdentifierOrKeyword(XPathExpr.IdentifierOrKeywordContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#reservedWord}.
     *
     * @param ctx the parse tree
     */
    void enterReservedWord(XPathExpr.ReservedWordContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#reservedWord}.
     *
     * @param ctx the parse tree
     */
    void exitReservedWord(XPathExpr.ReservedWordContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#keyword}.
     *
     * @param ctx the parse tree
     */
    void enterKeyword(XPathExpr.KeywordContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#keyword}.
     *
     * @param ctx the parse tree
     */
    void exitKeyword(XPathExpr.KeywordContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#literal}.
     *
     * @param ctx the parse tree
     */
    void enterLiteral(XPathExpr.LiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#literal}.
     *
     * @param ctx the parse tree
     */
    void exitLiteral(XPathExpr.LiteralContext ctx);

    /**
     * Enter a parse tree produced by {@link XPathExpr#numericLiteral}.
     *
     * @param ctx the parse tree
     */
    void enterNumericLiteral(XPathExpr.NumericLiteralContext ctx);

    /**
     * Exit a parse tree produced by {@link XPathExpr#numericLiteral}.
     *
     * @param ctx the parse tree
     */
    void exitNumericLiteral(XPathExpr.NumericLiteralContext ctx);
}