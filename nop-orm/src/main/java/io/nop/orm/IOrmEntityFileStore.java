package io.nop.orm;

public interface IOrmEntityFileStore {
    /**
     * 根据文件id获取
     * @param fileId
     * @return
     */
    String getFileLink(String fileId);

    String decodeFileId(String fileLink);

    void detachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    void attachFile(String fileId, String bizObjName,
                    String objId, String fieldName);
}
