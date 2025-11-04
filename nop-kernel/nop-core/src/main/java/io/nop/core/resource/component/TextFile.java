/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;

public class TextFile implements IComponentModel {
    private final SourceLocation location;
    private final String text;

    public TextFile(SourceLocation loc, String text) {
        this.location = loc;
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }
}
