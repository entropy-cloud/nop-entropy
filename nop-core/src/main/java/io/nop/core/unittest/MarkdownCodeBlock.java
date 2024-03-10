/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.unittest;

import io.nop.api.core.util.SourceLocation;

public class MarkdownCodeBlock {
    private SourceLocation location;
    private String type;
    private String source;

    public static MarkdownCodeBlock build(SourceLocation loc, String type, String source) {
        MarkdownCodeBlock block = new MarkdownCodeBlock();
        block.setLocation(loc);
        block.setType(type);
        block.setSource(source);
        return block;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
