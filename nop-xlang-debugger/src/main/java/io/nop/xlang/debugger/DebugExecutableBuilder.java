/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.compile.BuildExecutableProcessor;
import io.nop.xlang.exec.DebugIdentifierExecutable;

public class DebugExecutableBuilder extends BuildExecutableProcessor {

    @Override
    public IExecutableExpression processIdentifier(Identifier node, IXLangCompileScope context) {
        switch (node.getIdentifierKind()) {
            case SCOPE_VAR_REF:
                return new DebugIdentifierExecutable(node.getLocation(), node.getName());
        }
        return super.processIdentifier(node, context);
    }
}