package io.nop.match.pattern;

import io.nop.api.core.util.Guard;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.match.MatchConstants.KEY_PREFIX;
import static io.nop.match.MatchConstants.NAME_OR;

public class OrMatchPattern implements IMatchPattern {
    private final List<IMatchPattern> patterns;

    public OrMatchPattern(List<IMatchPattern> patterns) {
        this.patterns = Guard.notEmpty(patterns, "patterns");
    }

    @Override
    public Object toJson() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put(KEY_PREFIX, NAME_OR);
        ret.put("patterns", patterns.stream().map(IMatchPattern::toJson).collect(Collectors.toList()));
        return ret;
    }

    @Override
    public boolean matchValue(MatchState state, boolean collectError) {
        if (!collectError) {
            for (int i = 0, n = patterns.size(); i < n; i++) {
                if (patterns.get(i).matchValue(state, false))
                    return true;
            }
        } else {
            IValidationErrorCollector old = state.getErrorCollector();
            try {
                ListValidationErrorCollector collector = new ListValidationErrorCollector();
                state.setErrorCollector(collector);
                for (int i = 0, n = patterns.size(); i < n; i++) {
                    if (patterns.get(i).matchValue(state, true))
                        return true;
                    collector.clear();
                }
                collector.addTo(old);
            } finally {
                state.setErrorCollector(old);
            }
        }
        return false;
    }
}