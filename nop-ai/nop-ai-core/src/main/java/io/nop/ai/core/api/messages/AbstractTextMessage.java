package io.nop.ai.core.api.messages;

public abstract class AbstractTextMessage extends AiMessage {
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getThink(){
        return null;
    }

    public void setThink(String think){

    }
}
