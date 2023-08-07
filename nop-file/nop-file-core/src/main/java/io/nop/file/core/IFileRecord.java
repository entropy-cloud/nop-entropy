package io.nop.file.core;

import io.nop.core.resource.IResource;

public interface IFileRecord {
    String getFileId();

    String getFileName();

    String getFileExt();

    String getMimeType();

    long getLength();

    long getLastModified();

    String getBizObjName();

    String getBizObjId();

    String getFieldName();

    IResource getResource();
}
