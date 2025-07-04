/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;

import static io.nop.core.CoreErrors.ARG_PATH;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_EXISTS;
import static io.nop.core.CoreErrors.ERR_RESOURCE_WRITE_TO_STREAM_FAIL;

public class InMemoryTextResource extends AbstractResource {
    private static final long serialVersionUID = 3183161892032869993L;
    private String text;
    private int length = -1;
    private long lastModified;
    private boolean readonly;

    private boolean exists = true;

    public InMemoryTextResource(String path, String text) {
        super(path);
        checkSupportStream();
        this.text = text == null ? "" : text;
        this.exists = text != null;
    }

    private void checkExists() {
        if (!exists)
            throw new NopException(ERR_RESOURCE_NOT_EXISTS)
                    .param(ARG_PATH, getPath());
    }

    public String getText() {
        checkExists();
        return text;
    }

    @Override
    protected Object internalObj() {
        return text;
    }

    @Override
    public long length() {
        if (!exists)
            return -1L;

        if (length < 0) {
            length = StringHelper.utf8Length(text);
        }
        return length;
    }

    @Override
    public long lastModified() {
        if (!exists)
            return -1L;
        return lastModified;
    }

    @Override
    public void setLastModified(long time) {
        this.lastModified = time;
    }

    @Override
    public boolean exists() {
        return exists;
    }

    @Override
    public boolean delete() {
        exists = false;
        length = -1;
        text = "";
        lastModified = -1L;
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return readonly;
    }

    public void setReadOnly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public void saveToResource(IResource resource, IStepProgressListener listener) {
        checkExists();
        resource.writeText(text, null);
    }

    @Override
    public InputStream getInputStream() {
        checkExists();
        return new ByteArrayInputStream(text.getBytes(StringHelper.CHARSET_UTF8));
    }

    @Override
    public Reader getReader(String encoding) {
        checkExists();
        return new StringReader(text);
    }

    @Override
    public String readText() {
        checkExists();
        return text;
    }

    @Override
    public synchronized void writeText(String text, String encoding) {
        checkNotReadonly();
        this.text = text == null ? "" : text;
        this.length = -1;
        this.lastModified = CoreMetrics.currentTimeMillis();
        this.exists = true;
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        if (append) {
            String text = ConvertHelper.toString(this.text, "");
            ByteArrayOutputStream out = new ByteArrayOutputStream() {
                public void close() {
                    writeText(text + new String(toByteArray(), StringHelper.CHARSET_UTF8), StringHelper.ENCODING_UTF8);
                }
            };
            return out;
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream() {
                public void close() {
                    writeText(new String(toByteArray(), StringHelper.CHARSET_UTF8), StringHelper.ENCODING_UTF8);
                }
            };
            return out;
        }
    }

    @Override
    public void writeToStream(OutputStream os) {
        checkExists();
        String text = this.text;
        try {
            os.write(text.getBytes(StringHelper.CHARSET_UTF8));
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_WRITE_TO_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
    }
}