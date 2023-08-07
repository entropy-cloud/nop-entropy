package io.nop.file.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;

import java.io.InputStream;

@DataBean
public class UploadRequestBean {
    private InputStream inputStream;
    private String fileName;

    private String mimeType;
    private long length;

    private long lastModified;

    public UploadRequestBean() {
    }

    public UploadRequestBean(InputStream inputStream, String fileName, long length, String mimeType) {
        this.inputStream = inputStream;
        this.fileName = fileName;
        this.length = length;
        this.mimeType = mimeType;
        this.lastModified = CoreMetrics.currentTimeMillis();
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    @JsonIgnore
    public InputStream getInputStream() {
        return inputStream;
    }

    public String getFileName() {
        return fileName;
    }

    @JsonIgnore
    public String getFileExt() {
        return StringHelper.fileExt(fileName);
    }

    public long getLength() {
        return length;
    }
}
