/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;

public class JsonEncodeString extends ValueWithLocation {
    public JsonEncodeString(SourceLocation loc, Object value) {
        super(loc, value);
    }

    public String toJsonString() {
        return "@:" + JsonTool.instance().stringify(getValue(), null, "  ");
    }

    @Override
    protected ValueWithLocation newValueWithLocation(SourceLocation loc, Object value) {
        return new JsonEncodeString(loc, value);
    }
}
