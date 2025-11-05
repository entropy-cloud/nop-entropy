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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.match.MatchConstants.KEY_PREFIX;
import static io.nop.match.MatchConstants.NAME_AND;

public class AndMatchPattern implements IMatchPattern {
    private final List<IMatchPattern> patterns;

    public AndMatchPattern(List<IMatchPattern> patterns) {
        this.patterns = patterns;
    }

    @Override
    public Object toJson() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(KEY_PREFIX, NAME_AND);
        ret.put("patterns", patterns.stream().map(IMatchPattern::toJson).collect(Collectors.toList()));
        return ret;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        for (int i = 0, n = patterns.size(); i < n; i++) {
            if (!patterns.get(i).matchValue(state, collectError))
                return false;
        }
        return true;
    }
}
