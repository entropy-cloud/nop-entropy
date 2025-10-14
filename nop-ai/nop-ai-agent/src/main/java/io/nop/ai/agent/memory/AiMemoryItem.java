package io.nop.ai.agent.memory;

import io.nop.api.core.annotations.data.DataBean;

import java.time.LocalDateTime;

@DataBean
public class AiMemoryItem {
    private String key;
    private String type;
    private String content;
    private LocalDateTime createTime;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
