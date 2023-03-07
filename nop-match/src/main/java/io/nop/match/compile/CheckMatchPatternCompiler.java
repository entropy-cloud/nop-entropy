/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.CheckMatchPattern;

public class CheckMatchPatternCompiler implements IMatchPatternCompiler {
    public static final CheckMatchPatternCompiler INSTANCE = new CheckMatchPatternCompiler();

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        String exprString = StringHelper.toString(value, null);
        IEvalAction expr = config.getExprParser().parseExpr(loc, exprString);
        return new CheckMatchPattern(exprString, new ExprPredicate(expr));
    }
}
