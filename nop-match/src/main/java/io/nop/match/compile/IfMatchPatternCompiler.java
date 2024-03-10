/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.IfMatchPattern;

import java.util.Map;

import static io.nop.core.lang.json.utils.SourceLocationHelper.getPropLocation;
import static io.nop.match.compile.PatternCompileHelper.optionsToMap;
import static io.nop.match.compile.PatternCompileHelper.requireOption;

public class IfMatchPatternCompiler implements IMatchPatternCompiler {
    public static final IfMatchPatternCompiler INSTANCE = new IfMatchPatternCompiler();

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        Map<String, Object> options = optionsToMap(value);
        String exprString = (String) requireOption("if", options, "testExpr");
        IEvalAction testExpr = config.getExprParser().parseExpr(null, exprString);
        Object trueValue = requireOption("if", options, "true");
        Object falseValue = options.get("false");

        IMatchPattern truePattern = config.getCompileHelper().parseFromValue(getPropLocation(options, "true"),
                trueValue, config);

        IMatchPattern falsePattern = falseValue == null ? null
                : config.getCompileHelper().parseFromValue(getPropLocation(options, "false"), falseValue, config);
        return new IfMatchPattern(exprString, new ExprPredicate(testExpr), truePattern, falsePattern);
    }
}
