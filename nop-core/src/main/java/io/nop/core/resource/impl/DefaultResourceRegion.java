/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.impl;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.commons.io.stream.BoundedInputStream;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceRegion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.core.CoreErrors.ARG_FILE_RANGE;
import static io.nop.core.CoreErrors.ARG_LENGTH;
import static io.nop.core.CoreErrors.ARG_POS;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_SRC;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NO_LENGTH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_STREAM_SKIP_TO_POS_FAILED;
import static io.nop.core.CoreErrors.ERR_RESOURCE_WRITE_TO_STREAM_FAIL;

public class DefaultResourceRegion implements IResourceRegion {
    private final IResource resource;
    private final LongRangeBean range;

    public DefaultResourceRegion(IResource resource, LongRangeBean range) {
        this.resource = resource;
        this.range = Guard.notNull(range, "null resource range");
    }

    public String toString() {
        return "ResourceRegion[range=" + range + ",resource=" + resource + "]";
    }

    @Override
    public LongRangeBean getRange() {
        return range;
    }

    @Override
    public void writeToStream(OutputStream os, IStepProgressListener listener) {
        InputStream is = this.getInputStream();
        // if (is instanceof ITransferableInputStream) {
        // try {
        // ((ITransferableInputStream) is).transferFullyTo(Channels.newChannel(os), range.getBegin(), range.getEnd());
        // } catch (Exception e) {
        // throw EntropyException.adapt(e);
        // }
        // return;
        // }

        try {
            IoHelper.copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), listener);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void writeToStream(OutputStream os) {
        writeToStream(os, null);
    }

    @Override
    public InputStream getInputStream() {
        long size = resource.length();
        if (size < 0)
            throw new NopException(ERR_RESOURCE_NO_LENGTH).param(ARG_RESOURCE, resource);

        if (range.getOffset() >= size)
            throw new NopException(ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE).param(ARG_FILE_RANGE, range)
                    .param(ARG_LENGTH, size).param(ARG_RESOURCE, resource);

        if (range.hasLimit() && range.getEnd() > size)
            throw new NopException(ERR_RESOURCE_NOT_CONTAINS_FILE_RANGE).param(ARG_FILE_RANGE, range)
                    .param(ARG_LENGTH, size).param(ARG_RESOURCE, resource);

        InputStream is = null;
        try {
            is = resource.getInputStream();
            if (range.getOffset() > 0) {
                long begin = is.skip(range.getOffset());
                if (begin != range.getOffset())
                    throw new NopException(ERR_RESOURCE_STREAM_SKIP_TO_POS_FAILED).param(ARG_POS, range)
                            .param(ARG_RESOURCE, this);
            }

            is = new BoundedInputStream(is, range.getLimit());
            return is;
        } catch (IOException e) {
            IoHelper.safeClose(is);
            throw new NopException(ERR_RESOURCE_WRITE_TO_STREAM_FAIL, e).param(ARG_SRC, resource);
        }
    }
}