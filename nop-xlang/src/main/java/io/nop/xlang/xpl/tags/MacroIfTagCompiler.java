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
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_CP_EXPR;
import static io.nop.xlang.xpl.XplConstants.TEST_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getCpValue;
import static io.nop.xlang.xpl.utils.XplParseHelper.notCpValue;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrExpr;
import static java.util.Arrays.asList;

public class MacroIfTagCompiler implements IXplTagCompiler {
    public static final MacroIfTagCompiler INSTANCE = new MacroIfTagCompiler();

    static final List<String> ATTR_NAMES = asList(TEST_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);
        Expression test = requireAttrExpr(node, TEST_NAME, cp, scope);
        if (notCpValue(test))
            throw new NopEvalException(ERR_XPL_ATTR_NOT_CP_EXPR).param(ARG_NODE, node).param(ARG_ATTR_NAME, TEST_NAME);

        Object b = getCpValue(test);
        if (ConvertHelper.toTruthy(b)) {
            return cp.parseTagBody(node, scope);
        }
        return null;
    }
}