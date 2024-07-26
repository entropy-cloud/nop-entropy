/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.dao.store;

import io.nop.core.resource.IResource;
import io.nop.file.core.IFileRecord;
import io.nop.file.dao.entity.NopFileRecord;

import java.sql.Timestamp;

public class DaoFileRecord implements IFileRecord {
    private final NopFileRecord record;
    private final IResource resource;

    public DaoFileRecord(NopFileRecord record, IResource resource) {
        this.record = record;
        this.resource = resource;
    }

    @Override
    public String getFileName() {
        return record.getFileName();
    }

    @Override
    public String getFileId() {
        return record.getFileId();
    }

    @Override
    public String getFileExt() {
        return record.getFileExt();
    }

    @Override
    public String getMimeType() {
        return record.getMimeType();
    }

    @Override
    public long getLength() {
        Long len = record.getFileLength();
        return len == null ? -1 : len;
    }

    @Override
    public long getLastModified() {
        Timestamp time = record.getFileLastModified();
        return time == null ? -1 : time.getTime();
    }

    @Override
    public String getBizObjName() {
        return record.getBizObjName();
    }

    @Override
    public String getBizObjId() {
        return record.getBizObjId();
    }

    @Override
    public String getFieldName() {
        return record.getFieldName();
    }

    @Override
    public IResource getResource() {
        return resource;
    }

    @Override
    public boolean isPublic() {
        return Boolean.TRUE.equals(record.getIsPublic());
    }
}