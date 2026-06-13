package io.nop.ai.agent.memory;

import io.nop.api.core.annotations.data.DataBean;

import java.time.LocalDateTime;

@DataBean
public class AiMemoryItem {
    private String key;
    private String type;
    private String content;
    private LocalDateTime createTime;
    private int priority;
    private int tokenEstimate = -1;
    private boolean pinned;
    private String checksum;
    private LocalDateTime lastAccessTime;
    private int accessCount;

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

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getTokenEstimate() {
        if (tokenEstimate == -1) {
            if (content == null)
                return 0;
            return content.length() / 4;
        }
        return tokenEstimate;
    }

    public void setTokenEstimate(int tokenEstimate) {
        this.tokenEstimate = tokenEstimate;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public LocalDateTime getLastAccessTime() {
        return lastAccessTime != null ? lastAccessTime : createTime;
    }

    public void setLastAccessTime(LocalDateTime lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }
}
