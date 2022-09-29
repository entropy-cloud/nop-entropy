package io.nop.match;

import io.nop.commons.functional.IEqualsChecker;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalExprParser;
import io.nop.core.lang.json.utils.JsonMatchHelper;
import io.nop.match.compile.PatternMatchPatternCompiler;

public class MatchPatternCompileConfig {
    private IEvalExprParser exprParser = EvalExprProvider.getDefaultExprParser();
    private IMatchPatternCompiler compileHelper = PatternMatchPatternCompiler.INSTANCE;
    private IEqualsChecker<Object> equalsChecker = JsonMatchHelper::valueEquals;
    private MatchPatternCompilerRegistry registry = MatchPatternCompilerRegistry.DEFAULT;

    public IEvalExprParser getExprParser() {
        if (exprParser == null)
            throw new IllegalStateException("nop.err.match.expr-not-supported");
        return exprParser;
    }

    public void setExprParser(IEvalExprParser exprParser) {
        this.exprParser = exprParser;
    }

    public IMatchPatternCompiler getCompileHelper() {
        return compileHelper;
    }

    public void setCompileHelper(IMatchPatternCompiler compileHelper) {
        this.compileHelper = compileHelper;
    }

    public IEqualsChecker<Object> getEqualsChecker() {
        return equalsChecker;
    }

    public void setEqualsChecker(IEqualsChecker<Object> equalsChecker) {
        this.equalsChecker = equalsChecker;
    }

    public MatchPatternCompilerRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(MatchPatternCompilerRegistry registry) {
        this.registry = registry;
    }
}
