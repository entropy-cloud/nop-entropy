/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalOutput;
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
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object v = executor.execute(value, rt);
        if (v == null)
            return null;
        IEvalOutput out = rt.getOut();
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
