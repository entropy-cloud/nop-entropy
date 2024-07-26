/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.api.core.util.FutureHelper;
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
    CompletionStage<?> batchLoadResourceAsync(Collection<String> fileIds);

    default void batchLoadResource(Collection<String> fileIds) {
        CompletionStage<?> ret = batchLoadResourceAsync(fileIds);
        FutureHelper.syncGet(ret);
    }

    FileStatusBean getFileStatus(String fileId, String bizObjName, String objId, String fieldName);

    IResource getFileResource(String fileId, String bizObjName, String objId, String fieldName);

    void detachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    void attachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    String copyFile(String fileId, String newBizObjName, String newObjId, String newFieldName);

    /**
     * 设置是否允许公开访问的标识
     *
     * @param fileId   文件id
     * @param isPublic 是否允许公开访问
     */
    void changePublic(String fileId, boolean isPublic);
}
