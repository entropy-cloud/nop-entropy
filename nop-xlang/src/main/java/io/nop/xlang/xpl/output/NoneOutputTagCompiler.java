/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.output;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.XLangParseBuffer;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_OUTPUT_MODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_ALLOW_OUTPUT;

public class NoneOutputTagCompiler implements IXplUnknownTagCompiler {
    public static final NoneOutputTagCompiler INSTANCE = new NoneOutputTagCompiler();

    @Override
    public void parseContent(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        throw new NopEvalException(ERR_XPL_NOT_ALLOW_OUTPUT).param(ARG_OUTPUT_MODE, XLangOutputMode.none).source(node)
                .param(ARG_NODE, node);
    }

    @Override
    public void parseTag(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        throw new NopEvalException(ERR_XPL_NOT_ALLOW_OUTPUT).param(ARG_OUTPUT_MODE, XLangOutputMode.none).source(node)
                .param(ARG_NODE, node);
    }
}