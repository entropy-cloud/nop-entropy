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
import io.nop.commons.text.RawText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.ast.XLangEscapeMode;

public class EscapeOutputExecutable extends AbstractExecutable {
    private final XLangEscapeMode escapeMode;
    private final IExecutableExpression valueExpr;

    public EscapeOutputExecutable(SourceLocation loc, XLangEscapeMode escapeMode, IExecutableExpression valueExpr) {
        super(loc);
        this.escapeMode = Guard.notNull(escapeMode, "escapeMode");
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr");
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("escape:");
        sb.append(escapeMode);
        sb.append(",");
        valueExpr.display(sb);
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        Object value = executor.execute(valueExpr, rt);
        if (value == null)
            return null;

        SourceLocation loc = getLocation();
        IEvalOutput out = rt.getOut();

        if (value instanceof RawText) {
            out.text(loc, ((RawText) value).getText());
            return null;
        }

        switch (escapeMode) {
            case xml: {
                String text = StringHelper.escapeXml(value.toString());
                out.text(loc, text);
                break;
            }
            case xmlAttr: {
                String text = StringHelper.escapeXmlAttr(value.toString());
                out.text(loc, text);
                break;
            }
            case xmlValue: {
                String text = StringHelper.escapeXmlValue(value.toString());
                out.text(loc, text);
                break;
            }
            default:
                out.value(loc, value);
        }
        return null;
    }
}