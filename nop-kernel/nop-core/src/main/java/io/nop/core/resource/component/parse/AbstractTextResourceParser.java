/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

public abstract class AbstractTextResourceParser<T> extends AbstractResourceParser<T>
        implements ITextResourceParser<T> {
    private String encoding;

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    protected T doParseResource(IResource resource) {
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        return doParseText(loc, ResourceHelper.readText(resource, getEncoding()));
    }

    protected abstract T doParseText(SourceLocation loc, String text);

    @Override
    public T parseFromText(SourceLocation loc, String text) {
        if (loc != null)
            this.setResourcePath(loc.getPath());
        return doParseText(loc, text);
    }
}
