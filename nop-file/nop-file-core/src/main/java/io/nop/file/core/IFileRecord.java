package io.nop.file.core;

import io.nop.core.resource.IResource;

public interface IFileRecord {
    String getFileName();

    String getFileId();

    String getFileExt();

    String getContentType();

    long getLength();

    long getLastModified();

    IResource getResource();
}