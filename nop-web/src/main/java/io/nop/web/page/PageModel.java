/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;

import java.util.Map;

public class PageModel implements IComponentModel {
    private final SourceLocation location;
    private final Map<String, Object> data;

    public PageModel(SourceLocation location, Map<String, Object> data) {
        this.location = location;
        this.data = data;
    }

    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }
}
