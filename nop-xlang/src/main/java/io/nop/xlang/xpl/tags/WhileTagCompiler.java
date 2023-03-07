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
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.List;

import static io.nop.xlang.ast.XLangASTBuilder.whileStatement;
import static io.nop.xlang.xpl.XplConstants.TEST_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrExpr;
import static java.util.Arrays.asList;

public class WhileTagCompiler implements IXplTagCompiler {
    public static final WhileTagCompiler INSTANCE = new WhileTagCompiler();

    static final List<String> ATTR_NAMES = asList(TEST_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);
        Expression test = requireAttrExpr(node, TEST_NAME, cp, scope);
        Expression body = cp.parseTagBody(node, scope);
        return whileStatement(node.getLocation(), test, body);
    }
}