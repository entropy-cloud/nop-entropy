/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.ForRangeStatement;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NAMES;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_FOR_TAG_NOT_ALLOW_BOTH_ITEMS_AND_BEGIN_END;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_MISSING_ATTRS;
import static io.nop.xlang.ast.XLangASTBuilder.let;
import static io.nop.xlang.xpl.XplConstants.BEGIN_NAME;
import static io.nop.xlang.xpl.XplConstants.END_NAME;
import static io.nop.xlang.xpl.XplConstants.INDEX_NAME;
import static io.nop.xlang.xpl.XplConstants.ITEMS_NAME;
import static io.nop.xlang.xpl.XplConstants.STEP_NAME;
import static io.nop.xlang.xpl.XplConstants.VAR_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkNotSysVar;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrIdentifier;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExprOrInt;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class ForTagCompiler implements IXplTagCompiler {
    public static final ForTagCompiler INSTANCE = new ForTagCompiler();

    static final List<String> ATTR_NAMES = asList(VAR_NAME, ITEMS_NAME, INDEX_NAME, BEGIN_NAME, END_NAME, STEP_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);

        Identifier var = getAttrIdentifier(node, VAR_NAME, cp, scope);
        Identifier index = getAttrIdentifier(node, INDEX_NAME, cp, scope);
        checkNotSysVar(node, VAR_NAME, var);
        checkNotSysVar(node, INDEX_NAME, index);

        Expression itemsExpr = parseAttrExpr(node, ITEMS_NAME, cp, scope);
        Expression beginExpr = parseAttrExprOrInt(node, BEGIN_NAME, cp, scope);
        Expression endExpr = parseAttrExprOrInt(node, END_NAME, cp, scope);
        Expression stepExpr = parseAttrExprOrInt(node, STEP_NAME, cp, scope);

        Expression body = cp.parseTagBody(node, scope);

        if (var == null) {
            var = Identifier.valueOf(node.getLocation(), scope.generateVarName(XLangConstants.GEN_VAR_PREFIX));
        }

        if (itemsExpr == null) {
            if (beginExpr == null && endExpr == null)
                throw new NopEvalException(ERR_XPL_TAG_MISSING_ATTRS).param(ARG_NAMES, singletonList(ITEMS_NAME));
            if (beginExpr == null)
                throw new NopEvalException(ERR_XPL_TAG_MISSING_ATTRS).param(ARG_NAMES, singletonList(BEGIN_NAME));
            if (endExpr == null) {
                throw new NopEvalException(ERR_XPL_TAG_MISSING_ATTRS).param(ARG_NAMES, singletonList(END_NAME));
            }

            // for( begin to end)
            ForRangeStatement stm = new ForRangeStatement();
            stm.setLocation(node.getLocation());
            stm.setBegin(beginExpr);
            stm.setEnd(endExpr);
            stm.setStep(stepExpr);
            stm.setVar(var);
            stm.setIndex(index);
            stm.setBody(body);
            return stm;
        } else {
            if (beginExpr != null || endExpr != null) {
                throw new NopEvalException(ERR_XPL_FOR_TAG_NOT_ALLOW_BOTH_ITEMS_AND_BEGIN_END).param(ARG_NODE, node);
            }

            // for(var of items)
            ForOfStatement stm = ForOfStatement.valueOf(node.getLocation(), let(var.getLocation(), var, null, null),
                    itemsExpr, body);
            stm.setIndex(index);

            return stm;
        }
    }
}