package io.nop.file.core.chunk;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ChunkResponseBean {
    private String eTag;

    @JsonProperty("eTag")
    public String getETag() {
        return eTag;
    }

    public void setETag(String eTag) {
        this.eTag = eTag;
    }
}
