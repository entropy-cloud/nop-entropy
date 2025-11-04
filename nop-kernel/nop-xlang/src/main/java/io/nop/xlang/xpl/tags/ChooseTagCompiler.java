/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.objects.OptionalValue;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.IConditionalExpression;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_SLOT_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_CHOOSE_CHILD_NOT_CONDITIONAL_EXPR;
import static io.nop.xlang.XLangErrors.ERR_XPL_MISSING_SLOT;
import static io.nop.xlang.xpl.XplConstants.OTHERWISE_NAME;
import static io.nop.xlang.xpl.XplConstants.TEST_NAME;
import static io.nop.xlang.xpl.XplConstants.WHEN_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkNoArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getSlot;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.staticValue;

public class ChooseTagCompiler implements IXplTagCompiler {
    public static final ChooseTagCompiler INSTANCE = new ChooseTagCompiler();

    static final List<String> WHEN_ATTRS = Arrays.asList(TEST_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkNoArgNames(node);

        if (!node.hasChild())
            throw new NopEvalException(ERR_XPL_MISSING_SLOT).param(ARG_SLOT_NAME, WHEN_NAME).param(ARG_NODE, node);

        List<Expression> exprs = new ArrayList<>(node.getChildCount() * 2);

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals(OTHERWISE_NAME))
                continue;
            IConditionalExpression cond = compileConditional(child, cp, scope);
            if (cond == null)
                continue;

            OptionalValue value = staticValue(cond.getTest());
            if (value.isPresent()) {
                if (value.asTruthy()) {
                    if (exprs.isEmpty()) {
                        cond.getConsequent().setASTParent(null);
                        return cond.getConsequent();
                    } else {
                        exprs.add(Literal.booleanValue(cond.getTest().getLocation(), value.asTruthy()));
                        exprs.add(cond.getConsequent());
                        continue;
                    }
                } else {
                    continue;
                }
            }
            cond.getTest().setASTParent(null);
            cond.getConsequent().setASTParent(null);
            exprs.add(cond.getTest());
            exprs.add(cond.getConsequent());
        }
        XNode otherwise = getSlot(node, OTHERWISE_NAME);
        if (otherwise != null) {
            Expression alternate = cp.parseTagBody(otherwise, scope);
            if (alternate != null)
                exprs.add(alternate);
        }

        if (exprs.isEmpty())
            return null;

        if (exprs.size() == 1)
            return exprs.get(0);

        return IfStatement.valueOf(node.getLocation(), exprs);
    }

    IConditionalExpression compileConditional(XNode child, IXplCompiler cp, IXLangCompileScope scope) {
        if (child.getTagName().equals(WHEN_NAME)) {
            return parseWhen(child, cp, scope);
        }
        Expression expr = cp.parseTag(child, scope);
        if (expr == null)
            return null;
        return checkConditional(child, expr);
    }

    IfStatement parseWhen(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, WHEN_ATTRS);
        Expression test = requireAttrExpr(node, TEST_NAME, cp, scope);
        Expression body = cp.parseTagBody(node, scope);
        return IfStatement.valueOf(node.getLocation(), test, body, null,false);
    }

    IConditionalExpression checkConditional(XNode node, Expression expr) {
        if (!(expr instanceof IConditionalExpression))
            throw new NopEvalException(ERR_XPL_CHOOSE_CHILD_NOT_CONDITIONAL_EXPR).param(ARG_NODE, node);
        IConditionalExpression cond = (IConditionalExpression) expr;
        if (cond.getConsequent() == null || cond.getAlternate() != null)
            throw new NopEvalException(ERR_XPL_CHOOSE_CHILD_NOT_CONDITIONAL_EXPR).param(ARG_NODE, node);
        return cond;
    }
}