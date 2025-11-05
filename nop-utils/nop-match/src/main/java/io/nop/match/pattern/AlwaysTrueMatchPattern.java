/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.pattern;

import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

public class AlwaysTrueMatchPattern implements IMatchPattern {
    public static final AlwaysTrueMatchPattern INSTANCE = new AlwaysTrueMatchPattern();

    @Override
    public Object toJson() {
        return "*";
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        return true;
    }
}