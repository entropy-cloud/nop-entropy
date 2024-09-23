/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.type.IGenericType;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.IXLangExprParser;

import java.util.List;

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

    IEvalFunction compileScript(SourceLocation loc, String lang, String source,
                                List<? extends IFunctionArgument> args,
                                IGenericType returnType,
                                IXLangCompileScope scope);

}