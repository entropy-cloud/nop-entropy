/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match;

import io.nop.core.model.query.FilterOp;
import io.nop.match.compile.AndMatchPatternCompiler;
import io.nop.match.compile.BetweenMatchPatternCompiler;
import io.nop.match.compile.CheckMatchPatternCompiler;
import io.nop.match.compile.CompareOpMatchPatternCompiler;
import io.nop.match.compile.ExprMatchPatternCompiler;
import io.nop.match.compile.IfMatchPatternCompiler;
import io.nop.match.compile.OrMatchPatternCompiler;
import io.nop.match.compile.PatternMatchPatternCompiler;
import io.nop.match.compile.StaticPatternCompiler;
import io.nop.match.compile.SwitchMatchPatternCompiler;
import io.nop.match.compile.VarMatchPatternCompiler;
import io.nop.match.pattern.AlwaysFalseMatchPattern;
import io.nop.match.pattern.AlwaysTrueMatchPattern;
import io.nop.match.pattern.IsNullMatchPattern;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.core.model.query.FilterOp.ALWAYS_FALSE;
import static io.nop.core.model.query.FilterOp.BETWEEN;
import static io.nop.core.model.query.FilterOp.DEFAULT_ASSERT_OPS;
import static io.nop.core.model.query.FilterOp.DEFAULT_COMPARE_OPS;
import static io.nop.core.model.query.FilterOp.IS_NULL;
import static io.nop.match.MatchConstants.NAME_AND;
import static io.nop.match.MatchConstants.NAME_CHECK;
import static io.nop.match.MatchConstants.NAME_EXPR;
import static io.nop.match.MatchConstants.NAME_IF;
import static io.nop.match.MatchConstants.NAME_OR;
import static io.nop.match.MatchConstants.NAME_PATTERN;
import static io.nop.match.MatchConstants.NAME_SWITCH;
import static io.nop.match.MatchConstants.NAME_VAR;
import static io.nop.match.compile.StaticPatternCompiler.fromAssertOp;

public class MatchPatternCompilerRegistry {
    public static final MatchPatternCompilerRegistry DEFAULT = new MatchPatternCompilerRegistry();

    static {
        DEFAULT.addCompiler("*", new StaticPatternCompiler(AlwaysTrueMatchPattern.INSTANCE));
        DEFAULT.addCompiler(ALWAYS_FALSE.name(), new StaticPatternCompiler(AlwaysFalseMatchPattern.INSTANCE));

        for (FilterOp filterOp : DEFAULT_ASSERT_OPS) {
            DEFAULT.addCompiler(filterOp.name(), fromAssertOp(filterOp));
        }

        for (FilterOp filterOp : DEFAULT_COMPARE_OPS) {
            DEFAULT.addCompiler(filterOp.name(),
                    new CompareOpMatchPatternCompiler(filterOp.name(), filterOp.getBiPredicate()));
        }
        DEFAULT.addCompiler(BETWEEN.name(),
                new BetweenMatchPatternCompiler(BETWEEN.name(), BETWEEN.getBetweenOperator()));

        DEFAULT.addCompiler(IS_NULL.name(), new StaticPatternCompiler(IsNullMatchPattern.INSTANCE));
        DEFAULT.addCompiler(NAME_PATTERN, PatternMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_IF, IfMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_SWITCH, SwitchMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_OR, OrMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_AND, AndMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_EXPR, ExprMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_VAR, VarMatchPatternCompiler.INSTANCE);
        DEFAULT.addCompiler(NAME_CHECK, CheckMatchPatternCompiler.INSTANCE);
    }

    private final Map<String, IMatchPatternCompiler> compilers = new ConcurrentHashMap<>();

    public Map<String, IMatchPatternCompiler> getCompilers() {
        return compilers;
    }

    public IMatchPatternCompiler getCompiler(String name) {
        return compilers.get(name);
    }

    public void addCompiler(String name, IMatchPatternCompiler compiler) {
        compilers.put(name, compiler);
    }

    public void removeCompiler(String name, IMatchPatternCompiler compiler) {
        compilers.remove(name, compiler);
    }
}