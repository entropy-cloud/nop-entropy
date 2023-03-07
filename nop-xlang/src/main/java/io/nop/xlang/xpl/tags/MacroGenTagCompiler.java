/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import static io.nop.xlang.xpl.utils.XplParseHelper.checkNoArgNames;

public class MacroGenTagCompiler implements IXplTagCompiler {
    public static final MacroGenTagCompiler INSTANCE = new MacroGenTagCompiler();

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkNoArgNames(node);
        scope.enterMacro();
        ExprEvalAction action;
        try {
            action = new XLangCompileTool(scope).compileTagBody(node, XLangOutputMode.node);
        } finally {
            scope.leaveMacro();
        }

        XNode outNode = action.generateNode(scope);
        if (outNode == null)
            return null;

        return cp.parseTagBody(outNode, scope);
    }
}