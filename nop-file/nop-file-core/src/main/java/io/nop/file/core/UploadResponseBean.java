package io.nop.file.core;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class UploadResponseBean {
    private String value;
    private String filename;

    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
