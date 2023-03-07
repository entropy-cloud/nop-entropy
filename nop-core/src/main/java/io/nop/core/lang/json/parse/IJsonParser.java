/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.parse;

import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.component.parse.ITextResourceParser;

public interface IJsonParser extends ITextResourceParser<Object> {
    IJsonParser config(JsonParseOptions options);

    Object parseFromText(SourceLocation loc, String text);
}