/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.core.resource.IResource;

public interface IOrmEntityFileStore {
    /**
     * 根据文件id获取
     *
     * @param fileId
     * @return
     */
    String getFileLink(String fileId);

    String decodeFileId(String fileLink);

    IResource getFileResource(String fileId, String bizObjName, String objId, String fieldName);

    void detachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    void attachFile(String fileId, String bizObjName,
                    String objId, String fieldName);
}
