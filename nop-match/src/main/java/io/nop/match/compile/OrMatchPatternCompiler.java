/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.utils.SourceLocationHelper;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchConstants;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.OrMatchPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrMatchPatternCompiler implements IMatchPatternCompiler {
    public static OrMatchPatternCompiler INSTANCE = new OrMatchPatternCompiler();

    // tell cpd to start ignoring code - CPD-OFF
    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        Map<String, Object> options = PatternCompileHelper.optionsToMap(value);
        List<Object> children = (List<Object>) PatternCompileHelper.requireOption(MatchConstants.NAME_OR, options,
                "patterns");

        List<IMatchPattern> patterns = new ArrayList<>(children.size());
        for (int i = 0, n = children.size(); i < n; i++) {
            SourceLocation itemLoc = SourceLocationHelper.getElementLocation(children, i);
            patterns.add(config.getCompileHelper().parseFromValue(itemLoc, children.get(i), config));
        }
        return new OrMatchPattern(patterns);
    }
    // resume CPD analysis - CPD-ON
}
