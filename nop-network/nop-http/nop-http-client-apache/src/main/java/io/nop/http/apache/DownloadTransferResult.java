package io.nop.http.apache;

import java.io.File;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 一次下载传输的落地状态：由消费器填充，downloadAsync 在校验/改名后据此构造响应
 */
public class DownloadTransferResult {
    private int status;
    private Map<String, String> headers;
    private long startOffset;
    private long bytesWritten;
    private long totalBytes = -1;
    private MessageDigest sha256;
    private MessageDigest sha1;
    private File partFile;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(long startOffset) {
        this.startOffset = startOffset;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public void setBytesWritten(long bytesWritten) {
        this.bytesWritten = bytesWritten;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public MessageDigest getSha256() {
        return sha256;
    }

    public void setSha256(MessageDigest sha256) {
        this.sha256 = sha256;
    }

    public MessageDigest getSha1() {
        return sha1;
    }

    public void setSha1(MessageDigest sha1) {
        this.sha1 = sha1;
    }

    public File getPartFile() {
        return partFile;
    }

    public void setPartFile(File partFile) {
        this.partFile = partFile;
    }
}
