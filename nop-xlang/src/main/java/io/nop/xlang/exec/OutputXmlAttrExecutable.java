/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;

public class OutputXmlAttrExecutable extends AbstractExecutable {
    private final String name;
    private final IExecutableExpression value;

    public OutputXmlAttrExecutable(SourceLocation loc, String name, IExecutableExpression value) {
        super(loc);
        this.name = Guard.notEmpty(name, "name");
        this.value = Guard.notNull(value, "value");
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("@output:");
        sb.append(name).append('=');
        value.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, IEvalScope scope) {
        Object v = executor.execute(value, scope);
        if (v == null)
            return null;
        IEvalOutput out = scope.getOut();
        SourceLocation loc = getLocation();
        out.text(null, " ");
        out.text(null, name);
        out.text(loc, "=\"");
        String text = StringHelper.escapeXmlAttr(v.toString());
        out.text(loc, text);
        out.text(null, "\"");
        return null;
    }
}
