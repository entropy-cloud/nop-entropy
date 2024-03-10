/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.pattern;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.utils.SourceLocationHelper;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.api.core.ApiErrors.ARG_SIZE;
import static io.nop.match.MatchErrors.ARG_EXPECTED;
import static io.nop.match.MatchErrors.ERR_MATCH_FIELD_NOT_LIST;
import static io.nop.match.MatchErrors.ERR_MATCH_LIST_SIZE_NOT_MATCH;
import static io.nop.match.MatchErrors.ERR_MATCH_OBJECT_IS_NULL;

public class ListMatchPattern implements IMatchPattern {
    private final List<IMatchPattern> patterns;

    public ListMatchPattern(List<IMatchPattern> patterns) {
        this.patterns = patterns;
    }

    @Override
    public Object toJson() {
        return patterns.stream().map(IMatchPattern::toJson).collect(Collectors.toList());
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        Object value = state.getValue();
        if (value == null) {
            if (collectError) {
                state.buildError(ERR_MATCH_OBJECT_IS_NULL).addToCollector(state.getErrorCollector());
            }
            return false;
        }

        if (!(value instanceof Collection<?>)) {
            if (collectError) {
                state.buildError(ERR_MATCH_FIELD_NOT_LIST).addToCollector(state.getErrorCollector());
            }
            return false;
        }

        Object parent = state.getParent();
        try {
            return matchList((Collection) value, state, collectError);
        } finally {
            state.setParent(parent);
        }
    }

    private boolean matchList(Collection<?> list, MatchState state, boolean collectError) {
        if (patterns.size() != list.size()) {
            if (collectError) {
                state.buildError(ERR_MATCH_LIST_SIZE_NOT_MATCH).param(ARG_SIZE, list.size()).param(ARG_EXPECTED,
                        patterns.size());
            }
            return false;
        }

        boolean matched = true;
        Iterator<?> it = list.iterator();
        for (int i = 0, n = patterns.size(); i < n; i++) {
            SourceLocation loc = getLocation(list, i);
            Object value = it.next();

            state.setLocation(loc);
            state.setValue(value);
            state.setParent(list);
            state.enter(i);
            if (!patterns.get(i).matchValue(state, collectError)) {
                matched = false;
            }
            state.leave();
        }
        return matched;
    }

    SourceLocation getLocation(Object bean, int index) {
        return SourceLocationHelper.getElementLocation(bean, index);
    }
}