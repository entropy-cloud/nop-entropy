package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class BinaryDataBean {
    private final String contentType;
    private final byte[] data;

    public BinaryDataBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("data") byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}
