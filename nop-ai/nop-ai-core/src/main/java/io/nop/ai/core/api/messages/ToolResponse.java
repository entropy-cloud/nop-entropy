package io.nop.ai.core.api.messages;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class ToolResponse {
    private String id;
    private String name;

    private String responseData;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResponseData() {
        return responseData;
    }

    public void setResponseData(String responseData) {
        this.responseData = responseData;
    }
}
