package io.nop.file.core;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class DownloadRequestBean {
    private String fileId;
    private String contentType;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
