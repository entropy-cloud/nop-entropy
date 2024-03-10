/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match.compile;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.query.IBetweenOperator;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.BetweenMatchPattern;

import java.util.Map;

import static io.nop.match.MatchErrors.ARG_EXPR;
import static io.nop.match.MatchErrors.ERR_MATCH_INVALID_RANGE_EXPR;

public class BetweenMatchPatternCompiler implements IMatchPatternCompiler {
    private final String filterOp;
    private final IBetweenOperator operator;

    public BetweenMatchPatternCompiler(String filterOp, IBetweenOperator operator) {
        this.filterOp = filterOp;
        this.operator = operator;
    }

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        if (value instanceof String) {
            String str = value.toString();
            int pos = str.indexOf(',');
            if (pos < 0)
                throw new NopException(ERR_MATCH_INVALID_RANGE_EXPR).param(ARG_EXPR, str);

            Object min = JsonTool.parseSimpleJsonValue(str.substring(0, pos).trim());
            Object max = JsonTool.parseSimpleJsonValue(str.substring(pos + 1).trim());
            return new BetweenMatchPattern(filterOp, operator, min, max, false, false);
        }

        Map<String, Object> options = (Map<String, Object>) value;
        Object min = options.get("min");
        Object max = options.get("max");
        boolean excludeMin = ConvertHelper.toPrimitiveBoolean(options.get("excludeMin"));
        boolean excludeMax = ConvertHelper.toPrimitiveBoolean(options.get("excludeMax"));
        return new BetweenMatchPattern(filterOp, operator, min, max, excludeMin, excludeMax);
    }
}
