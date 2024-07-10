/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.utils.XplParseHelper;

public class PredicateTagCompiler implements IXplTagCompiler {
    public static final PredicateTagCompiler INSTANCE = new PredicateTagCompiler();

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        XplParseHelper.checkNoArgNames(node);
        return new FilterBeanExpressionCompiler(cp, scope).compilePredicate(node);
    }
}