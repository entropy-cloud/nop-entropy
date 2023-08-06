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
import io.nop.commons.util.IoHelper;

import java.io.InputStream;
import java.io.OutputStream;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_WRITE_TO_STREAM_FAIL;

/**
 * @author canonical_entropy@163.com
 */
public class InputStreamResource extends AbstractResource {

    private static final long serialVersionUID = 1641987530569373035L;

    private final InputStream is;
    private final long lastModified;

    private long length;

    /**
     * @param path
     */
    public InputStreamResource(String path, InputStream is, long lastModified, long length) {
        super(path);
        checkSupportStream();
        this.is = is;
        this.lastModified = lastModified;
        this.length = length;
    }

    public InputStreamResource(String path, InputStream is, long lastModified) {
        this(path, is, lastModified, -1);
    }

    @Override
    protected Object internalObj() {
        return is;
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
        return length;
    }

    @Override
    public InputStream getInputStream() {
        return is;
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        if (listener != null)
            listener.begin();

        try {
            IoHelper.copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), listener);
        } catch (Exception e) {
            throw new NopException(ERR_RESOURCE_WRITE_TO_STREAM_FAIL, e).param(ARG_RESOURCE, this);
        }
        if (listener != null) {
            listener.end();
        }
    }
}