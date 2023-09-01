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
