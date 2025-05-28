package io.nop.ai.core.api.tool;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class CallToolResult {
    private List<Content> content;
    private Boolean isError;

    public List<Content> getContent() {
        return content;
    }

    public void setContent(List<Content> content) {
        this.content = content;
    }

    public Boolean getIsError() {
        return isError;
    }

    public void setIsError(Boolean error) {
        isError = error;
    }
}