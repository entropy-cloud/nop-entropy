package io.nop.biz.api;

public interface IBizEntityFileStore {
    String getFileLink(String fileId);

    void detachFile(String fileId, String bizObjName,
                    String objId, String fieldName);

    void attachFile(String fileId, String bizObjName,
                    String objId, String fieldName);
}
