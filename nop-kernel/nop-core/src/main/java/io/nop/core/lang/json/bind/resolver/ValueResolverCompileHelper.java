/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;

import static io.nop.core.CoreErrors.ARG_TYPE;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_JSON_BIND_OPTIONS_NOT_STRING;

public class ValueResolverCompileHelper {
    public static String getStringConfig(String type, SourceLocation loc, Object value) {
        if (!(value instanceof String)) {
            throw new NopException(ERR_JSON_BIND_OPTIONS_NOT_STRING).loc(loc).param(ARG_TYPE, type).param(ARG_VALUE,
                    value);
        }
        return value.toString();
    }
}
