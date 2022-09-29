package io.nop.match.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.model.query.FilterOp;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.AssertOpMatchPattern;

public class StaticPatternCompiler implements IMatchPatternCompiler {
    private final IMatchPattern pattern;

    public StaticPatternCompiler(IMatchPattern pattern) {
        this.pattern = pattern;
    }

    public static StaticPatternCompiler fromAssertOp(FilterOp op) {
        return new StaticPatternCompiler(new AssertOpMatchPattern(op.name(), op.getPredicate()));
    }

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value,
                                        MatchPatternCompileConfig config) {
        return pattern;
    }
}
