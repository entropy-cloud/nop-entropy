/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.vue;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.TemplateExpression;
import io.nop.xlang.expr.ExprFeatures;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.expr.simple.SimpleExprParser;

import java.util.List;

public class VueExpressionParser extends SimpleExprParser {
    public VueExpressionParser() {
        setUseEvalException(false);
        setFeatures(ExprFeatures.SIMPLE);
    }

    public Expression parseTemplateExpr(SourceLocation loc, String source) {
        return parseTemplateExpr(loc, source, false, ExprPhase.eval);
    }

    @Override
    public boolean isExprStart(TextScanner sc, ExprPhase phase) {
        return sc.cur == '{' && sc.peek() == '{';
    }

    @Override
    protected void consumeExprEnd(TextScanner sc) {
        sc.consume("}}");
    }

    protected Expression newTemplateExpr(SourceLocation loc, List<Expression> exprs, ExprPhase phase) {
        if (exprs.isEmpty())
            return null;
        return TemplateExpression.valueOf(loc, exprs, "{{", "}}");
    }
}
