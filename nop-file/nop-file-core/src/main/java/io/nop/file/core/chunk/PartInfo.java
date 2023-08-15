package io.nop.file.core.chunk;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class PartInfo {
    private int partNumber;

    private String eTag;

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    @JsonProperty("eTag")
    public String getETag() {
        return eTag;
    }

    public void setETag(String eTag) {
        this.eTag = eTag;
    }
}
