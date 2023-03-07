/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
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
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        return pattern;
    }
}
