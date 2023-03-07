/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;

public interface IXplTagCompiler {
    default Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        XLangParseBuffer buf = new XLangParseBuffer();
        parseTag(buf, node, cp, scope);
        return buf.getResult();
    }

    default void parseTag(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        Expression expr = parseTag(node, cp, scope);
        if (expr != null) {
            buf.add(expr);
        }
    }
}