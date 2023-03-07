/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.ReturnStatement;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.Arrays;
import java.util.List;

import static io.nop.xlang.xpl.XplConstants.VALUE_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExpr;

public class ReturnTagCompiler implements IXplTagCompiler {
    public static final ReturnTagCompiler INSTANCE = new ReturnTagCompiler();

    static final List<String> ARG_NAMES = Arrays.asList(VALUE_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ARG_NAMES);
        Expression expr = parseAttrExpr(node, VALUE_NAME, cp, scope);
        return ReturnStatement.valueOf(node.getLocation(), expr);
    }
}
