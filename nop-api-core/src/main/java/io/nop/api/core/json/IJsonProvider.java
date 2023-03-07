/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.json;

import io.nop.api.core.util.SourceLocation;

import java.util.function.Function;

public interface IJsonProvider {
    Object parseFromText(SourceLocation loc, String str, JsonParseOptions options);

    String stringify(Object o, Function<String, String> transformer, String indent);

    default String stringify(Object o) {
        return stringify(o, null, null);
    }
}