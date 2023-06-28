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
import io.nop.xlang.ast.Literal;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import static io.nop.xlang.xpl.utils.XplParseHelper.checkNoArgNames;

public class AstTagCompiler implements IXplTagCompiler {
    public static final AstTagCompiler INSTANCE = new AstTagCompiler();

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkNoArgNames(node);
        Expression body = cp.parseTagBody(node, scope);
        if (body == null)
            body = Literal.nullValue(node.getLocation());

        return Literal.valueOf(node.getLocation(), body);
    }
}