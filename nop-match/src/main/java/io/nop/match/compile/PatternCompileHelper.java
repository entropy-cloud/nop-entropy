/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.match.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;

import java.util.Map;

import static io.nop.match.MatchErrors.ARG_OPTIONS;
import static io.nop.match.MatchErrors.ARG_PATTERN_NAME;
import static io.nop.match.MatchErrors.ARG_PROP_NAME;
import static io.nop.match.MatchErrors.ERR_MATCH_CONFIG_OPTIONS_NOT_MAP;
import static io.nop.match.MatchErrors.ERR_MATCH_NULL_PATTERN_PROP;

public class PatternCompileHelper {
    public static Map<String, Object> optionsToMap(Object options) {
        if (options instanceof Map)
            return (Map<String, Object>) options;

        if (options instanceof String)
            return (Map<String, Object>) JsonTool.parseNonStrict(options.toString());

        throw new NopException(ERR_MATCH_CONFIG_OPTIONS_NOT_MAP).param(ARG_OPTIONS, options);
    }

    public static Object requireOption(String pattern, Map<String, Object> options, String name) {
        Object value = options.get(name);
        if (value == null)
            throw new NopException(ERR_MATCH_NULL_PATTERN_PROP).param(ARG_PATTERN_NAME, pattern).param(ARG_PROP_NAME,
                    name);
        return value;
    }
}