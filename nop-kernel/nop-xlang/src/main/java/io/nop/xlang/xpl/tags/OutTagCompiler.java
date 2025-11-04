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
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangEscapeMode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_OUT_TAG_NOT_ALLOW_CHILD;
import static io.nop.xlang.xpl.XplConstants.ESCAPE_NAME;
import static io.nop.xlang.xpl.XplConstants.VALUE_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrEnum;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseContentTemplate;
import static java.util.Arrays.asList;

public class OutTagCompiler implements IXplTagCompiler {
    public static final OutTagCompiler INSTANCE = new OutTagCompiler();

    static final List<String> ATTR_NAMES = asList(ESCAPE_NAME, VALUE_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);
        if (node.hasChild())
            throw new NopEvalException(ERR_XPL_OUT_TAG_NOT_ALLOW_CHILD).param(ARG_NODE, node);

        XLangEscapeMode escape = getAttrEnum(node, ESCAPE_NAME, XLangEscapeMode.class, cp, scope);
        if (escape == null)
            escape = XLangEscapeMode.xml;
        Expression value = parseAttrExpr(node, VALUE_NAME, cp, scope);
        if (value == null) {
            value = parseContentTemplate(node, cp, scope);
        }
        if (value == null)
            return null;
        return EscapeOutputExpression.valueOf(node.getLocation(), escape, value);
    }
}
