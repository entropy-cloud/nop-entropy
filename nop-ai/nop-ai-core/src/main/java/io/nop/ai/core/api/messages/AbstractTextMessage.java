package io.nop.ai.core.api.messages;

public class AbstractTextMessage extends Message{

    protected String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public Object getMessageContent() {
        return getContent();
    }
}
