/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.convert.IByteArrayView;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IStepProgressListener;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_WRITE_TO_STREAM_FAIL;

/**
 * @author canonical_entropy@163.com
 */
public class ByteArrayResource extends AbstractResource implements IByteArrayView {

    private static final long serialVersionUID = 1641987530569373035L;

    private final byte[] data;
    private final long lastModified;

    /**
     * @param path
     */
    public ByteArrayResource(String path, byte[] data, long lastModified) {
        super(path);
        checkSupportStream();
        this.data = data == null ? EMPTY_BYTES : data;
        this.lastModified = lastModified;
    }

    public byte[] getData(){
        return data;
    }

    @Override
    protected Object internalObj() {
        return data;
    }

    @Override
    public long lastModified() {
        return lastModified;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public long length() {
        return data.length;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(data);
    }

    @Override
    public byte[] toByteArray() {
        return data;
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        if (listener != null)
            listener.begin();

        try {
            os.write(data);
        } catch (Exception e) {
            throw new NopException(ERR_RESOURCE_WRITE_TO_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
        if (listener != null) {
            listener.onStep(data.length);
            listener.end();
        }
    }
}