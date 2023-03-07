/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.JsonTool;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.CompareOpMatchPattern;

import java.util.function.BiPredicate;

public class CompareOpMatchPatternCompiler implements IMatchPatternCompiler {
    private final String filterOp;
    private final BiPredicate<Object, Object> predicate;

    public CompareOpMatchPatternCompiler(String filterOp, BiPredicate<Object, Object> predicate) {
        this.filterOp = filterOp;
        this.predicate = predicate;
    }

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        Object pattern = value instanceof String ? JsonTool.parseSimpleJsonValue(value.toString()) : value;
        return new CompareOpMatchPattern(filterOp, predicate, pattern);
    }
}