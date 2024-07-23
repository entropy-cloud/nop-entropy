/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.core.resource.IResource;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

public interface IOrmEntityFileStore {
    /**
     * 根据文件id获取
     *
     * @param fileId
     * @return
     */
    String getFileLink(String fileId);

    String decodeFileId(String fileLink);

    /**
     * 用于性能优化的函数，将对应的FileRecord加载到内存中
     *
     * @param fileIds 文件id列表
     */
    CompletionStage<?> batchLoadResource(Collection<String> fileIds);

    FileStatusBean getFileStatus(String fileId, String bizObjName, String objId, String fieldName);

    IResource getFileResource(String fileId, String bizObjName, String objId, String fieldName);

    void detachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    void attachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    String copyFile(String fileId, String newBizObjName, String newObjId, String newFieldName);
}
