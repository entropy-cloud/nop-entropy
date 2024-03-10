/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.output;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.XLangEscapeMode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.XLangParseBuffer;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_ALLOW_OUTPUT_TAG;
import static io.nop.xlang.xpl.output.OutputParseHelper.outputContent;

public class TextOutputTagCompiler implements IXplUnknownTagCompiler {
    public static final TextOutputTagCompiler INSTANCE = new TextOutputTagCompiler();

    @Override
    public void parseContent(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        outputContent(buf, node, XLangEscapeMode.none, cp, scope);
    }

    @Override
    public void parseTag(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        if (node.isTextNode()) {
            outputContent(buf, node, XLangEscapeMode.none, cp, scope);
            return;
        }

        throw new NopEvalException(ERR_XPL_NOT_ALLOW_OUTPUT_TAG).source(node).param(ARG_NODE, node);
    }
}
