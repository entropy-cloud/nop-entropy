/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.commons.io.stream.FastBufferedReader;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;

public abstract class AbstractCharReaderResourceParser<T> extends AbstractResourceParser<T>
        implements ITextResourceParser<T> {
    private String encoding;

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding == null ? StringHelper.ENCODING_UTF8 : encoding;
    }

    protected ICharReader toReader(IResource resource) {
        if (resource instanceof InMemoryTextResource)
            return new CharSequenceReader(((InMemoryTextResource) resource).getText());
        return new FastBufferedReader(resource.getReader(encoding));
    }

    @Override
    protected T doParseResource(IResource resource) {
        ICharReader reader = null;
        try {
            reader = toReader(resource);
            return doParse(SourceLocation.fromPath(resource.getPath()), reader);
        } finally {
            IoHelper.safeClose(reader);
        }
    }

    @Override
    public T parseFromText(SourceLocation loc, String s) {
        if (s == null)
            return null;
        return doParse(loc, new CharSequenceReader(s));
    }

    protected abstract T doParse(SourceLocation loc, ICharReader reader);
}
