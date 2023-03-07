/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.collections.iterator.IntRangeIterator;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.nop.core.type.PredefinedGenericTypes.INT_TYPE;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_NAMES;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_CP_EXPR;
import static io.nop.xlang.XLangErrors.ERR_XPL_FOR_TAG_NOT_ALLOW_BOTH_ITEMS_AND_BEGIN_END;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_MISSING_ATTRS;
import static io.nop.xlang.xpl.XplConstants.BEGIN_NAME;
import static io.nop.xlang.xpl.XplConstants.END_NAME;
import static io.nop.xlang.xpl.XplConstants.INDEX_NAME;
import static io.nop.xlang.xpl.XplConstants.ITEMS_NAME;
import static io.nop.xlang.xpl.XplConstants.STEP_NAME;
import static io.nop.xlang.xpl.XplConstants.VAR_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkNotSysVar;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrIdentifier;
import static io.nop.xlang.xpl.utils.XplParseHelper.getCpValue;
import static io.nop.xlang.xpl.utils.XplParseHelper.notCpValue;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExprOrInt;
import static io.nop.xlang.xpl.utils.XplParseHelper.registerMacroVar;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class MacroForTagCompiler implements IXplTagCompiler {
    public static final MacroForTagCompiler INSTANCE = new MacroForTagCompiler();

    static final List<String> ATTR_NAMES = asList(VAR_NAME, ITEMS_NAME, INDEX_NAME, BEGIN_NAME, END_NAME, STEP_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        // tell cpd to start ignoring code - CPD-OFF
        checkArgNames(node, ATTR_NAMES);

        Identifier var = getAttrIdentifier(node, VAR_NAME, cp, scope);
        Identifier index = getAttrIdentifier(node, INDEX_NAME, cp, scope);
        checkNotSysVar(node, VAR_NAME, var);
        checkNotSysVar(node, INDEX_NAME, index);

        Expression itemsExpr = parseAttrExpr(node, ITEMS_NAME, cp, scope);
        Expression beginExpr = parseAttrExprOrInt(node, BEGIN_NAME, cp, scope);
        Expression endExpr = parseAttrExprOrInt(node, END_NAME, cp, scope);
        Expression stepExpr = parseAttrExprOrInt(node, STEP_NAME, cp, scope);

        Runnable varCleanup = null;
        if (var != null) {
            varCleanup = registerMacroVar(scope, var.getLocation(), var.getName(), INT_TYPE, null);
        }
        // resume CPD analysis - CPD-ON

        try {
            if (itemsExpr == null) {
                if (beginExpr == null && endExpr == null)
                    throw new NopEvalException(ERR_XPL_TAG_MISSING_ATTRS).param(ARG_NAMES, singletonList(ITEMS_NAME));
                if (beginExpr == null)
                    throw new NopEvalException(ERR_XPL_TAG_MISSING_ATTRS).param(ARG_NAMES, singletonList(BEGIN_NAME));
                if (endExpr == null) {
                    throw new NopEvalException(ERR_XPL_TAG_MISSING_ATTRS).param(ARG_NAMES, singletonList(END_NAME));
                }

                if (!notCpValue(beginExpr))
                    throw new NopEvalException(ERR_XPL_ATTR_NOT_CP_EXPR).param(ARG_NODE, node).param(ARG_ATTR_NAME,
                            BEGIN_NAME);

                if (!notCpValue(endExpr))
                    throw new NopEvalException(ERR_XPL_ATTR_NOT_CP_EXPR).param(ARG_NODE, node).param(ARG_ATTR_NAME,
                            END_NAME);

                if (!notCpValue(stepExpr))
                    throw new NopEvalException(ERR_XPL_ATTR_NOT_CP_EXPR).param(ARG_NODE, node).param(ARG_ATTR_NAME,
                            STEP_NAME);

                Integer begin = getCpInteger(beginExpr, node, BEGIN_NAME);
                Integer end = getCpInteger(endExpr, node, END_NAME);
                Integer step = getCpInteger(stepExpr, node, STEP_NAME);
                if (begin == null)
                    begin = 0;
                if (end == null)
                    end = 0;
                if (step == null)
                    step = 1;

                if (step == 0)
                    throw new NopEvalException(ERR_EXEC_LOOP_STEP_MUST_NOT_BE_ZERO).param(ARG_NODE, node);

                IntRangeIterator it = new IntRangeIterator(begin, end, step);
                return buildBody(it, node, var, index, cp, scope);
            } else {
                if (beginExpr != null || endExpr != null) {
                    throw new NopEvalException(ERR_XPL_FOR_TAG_NOT_ALLOW_BOTH_ITEMS_AND_BEGIN_END).param(ARG_NODE,
                            node);
                }

                // for(var of items)
                Object items = getCpValue(itemsExpr);
                Iterator<?> it = CollectionHelper.toIterator(items, false,
                        err -> new NopEvalException(err).param(ARG_NODE, node).param(ARG_ATTR_NAME, ITEMS_NAME));

                return buildBody(it, node, var, index, cp, scope);
            }
        } finally {
            if (varCleanup != null)
                varCleanup.run();
        }
    }

    private Expression buildBody(Iterator<?> it, XNode node, Identifier var, Identifier index, IXplCompiler cp,
                                 IXLangCompileScope scope) {
        List<Expression> list = new ArrayList<>();
        int i = 0;
        while (it.hasNext()) {
            Object value = it.next();

            if (var != null)
                scope.setLocalValue(var.getLocation(), var.getName(), value);

            if (index != null) {
                scope.setLocalValue(var.getLocation(), index.getName(), i);
                i++;
            }

            Expression body = cp.parseTagBody(node, scope);
            list.add(body);
        }
        return SequenceExpression.valueOf(node.getLocation(), list);
    }

    private Integer getCpInteger(Expression expr, XNode node, String attrName) {
        Object value = getCpValue(expr);
        return ConvertHelper.toInt(value,
                err -> new NopEvalException(err).param(ARG_ATTR_NAME, attrName).param(ARG_NODE, node));
    }

}