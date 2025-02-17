package io.nop.ai.core.api.messages;

public abstract class AbstractTextMessage extends Message {
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
