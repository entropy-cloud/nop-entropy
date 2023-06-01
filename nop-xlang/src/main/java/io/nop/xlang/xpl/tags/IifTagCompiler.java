/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.Arrays;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_IIF_NODE_MUST_HAS_TWO_CHILD;
import static io.nop.xlang.xpl.XplConstants.TEST_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrExpr;

public class IifTagCompiler implements IXplTagCompiler {
    public static final IifTagCompiler INSTANCE = new IifTagCompiler();

    static final List<String> WHEN_ATTRS = Arrays.asList(TEST_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, WHEN_ATTRS);

        if (node.getChildCount() != 2)
            throw new NopEvalException(ERR_XPL_IIF_NODE_MUST_HAS_TWO_CHILD).param(ARG_NODE, node);

        Expression test = requireAttrExpr(node, TEST_NAME, cp, scope);
        Expression body = cp.parseTag(node.child(0), scope);
        Expression alternate = cp.parseTag(node.child(1), scope);
        return IfStatement.valueOf(node.getLocation(), test, body, alternate);
    }
}