/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
