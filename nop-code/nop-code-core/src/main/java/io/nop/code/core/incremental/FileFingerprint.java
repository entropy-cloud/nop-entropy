package io.nop.code.core.incremental;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 文件指纹，记录文件的唯一标识信息用于增量检测
 */
@DataBean
public class FileFingerprint {
    private String filePath;
    private String contentHash;
    private long lastModified;
    private long fileSize;

    public FileFingerprint() {
    }

    public FileFingerprint(String filePath, String contentHash, long lastModified, long fileSize) {
        this.filePath = filePath;
        this.contentHash = contentHash;
        this.lastModified = lastModified;
        this.fileSize = fileSize;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
