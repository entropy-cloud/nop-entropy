/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.pattern;

import io.nop.commons.util.StringHelper;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static io.nop.match.MatchConstants.KEY_PREFIX;
import static io.nop.match.MatchConstants.NAME_SWITCH;

public class SwitchMatchPattern implements IMatchPattern {
    private final String chooseExpr;
    private final Function<MatchState, String> chooseAction;
    private final Map<String, IMatchPattern> cases;
    private final IMatchPattern defaultPattern;

    public SwitchMatchPattern(String chooseExpr, Function<MatchState, String> chooseAction,
                              Map<String, IMatchPattern> cases, IMatchPattern defaultPattern) {
        this.chooseExpr = chooseExpr;
        this.chooseAction = chooseAction;
        this.cases = cases;
        this.defaultPattern = defaultPattern;
    }

    public Object toJson() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(KEY_PREFIX, NAME_SWITCH);
        ret.put("chooseExpr", chooseExpr);

        Map<String, Object> casesPattern = new LinkedHashMap<>();
        for (Map.Entry<String, IMatchPattern> entry : cases.entrySet()) {
            casesPattern.put(entry.getKey(), entry.getValue().toJson());
        }
        ret.put("cases", casesPattern);

        if (defaultPattern != null)
            ret.put("default", defaultPattern.toJson());
        return ret;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        String tag = chooseAction.apply(state);
        IMatchPattern pattern = choosePattern(tag);
        if (pattern == null) {
            return false;
        }
        return pattern.matchValue(state, collectError);
    }

    IMatchPattern choosePattern(String tag) {
        if (StringHelper.isEmpty(tag))
            return defaultPattern;
        IMatchPattern pattern = cases.get(tag);
        if (pattern == null)
            pattern = defaultPattern;
        return pattern;
    }
}