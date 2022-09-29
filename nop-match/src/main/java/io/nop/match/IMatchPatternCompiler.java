package io.nop.match;

import io.nop.api.core.util.SourceLocation;

public interface IMatchPatternCompiler {
    IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config);
}