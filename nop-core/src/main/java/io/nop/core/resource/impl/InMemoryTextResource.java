/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_WRITE_TO_STREAM_FAIL;

public class InMemoryTextResource extends AbstractResource {
    private static final long serialVersionUID = 3183161892032869993L;
    private String text;
    private int length = -1;
    private long lastModified;
    private boolean readonly;

    public InMemoryTextResource(String path, String text) {
        super(path);
        checkSupportStream();
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    protected Object internalObj() {
        return text;
    }

    @Override
    public long length() {
        if (length < 0) {
            length = StringHelper.utf8Length(text);
        }
        return length;
    }

    @Override
    public long lastModified() {
        return lastModified;
    }

    @Override
    public void setLastModified(long time) {
        this.lastModified = time;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return readonly;
    }

    @Override
    public void saveToResource(IResource resource, IStepProgressListener listener) {
        resource.writeText(text);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(text.getBytes(StringHelper.CHARSET_UTF8));
    }

    @Override
    public Reader getReader(String encoding) {
        return new StringReader(text);
    }

    @Override
    public String readText() {
        return text;
    }

    @Override
    public void writeText(String text) {
        checkNotReadonly();
        this.text = text == null ? "" : text;
        this.length = -1;
    }

    @Override
    public void writeToStream(OutputStream os) {
        try {
            os.write(text.getBytes(StringHelper.CHARSET_UTF8));
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_WRITE_TO_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }
}