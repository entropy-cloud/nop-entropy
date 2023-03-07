/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.IXLangExprParser;

public interface IXplCompiler extends IXLangExprParser {

    IXLangCompileScope newCompileScope();

    Expression parseTag(XNode node, IXLangCompileScope scope);

    void parseTag(XLangParseBuffer buf, XNode node, IXLangCompileScope scope);

    /**
     * 只编译标签的body段，忽略本标签的标签名/属性。
     */
    Expression parseTagBody(XNode node, IXLangCompileScope scope);

    void parseTagBody(XLangParseBuffer buf, XNode node, IXLangCompileScope scope);

    IXplTagLib loadLib(SourceLocation loc, String namespace, String src, IXLangCompileScope scope);

    IEvalFunction compileScript(SourceLocation loc, String lang, String source, IXLangCompileScope scope);

}