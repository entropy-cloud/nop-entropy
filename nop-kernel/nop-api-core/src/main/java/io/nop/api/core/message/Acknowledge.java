package io.nop.api.core.message;

public final class Acknowledge {
    private final Object replyMessage;

    public Acknowledge(Object replyMessage) {
        this.replyMessage = replyMessage;
    }

    public Object getReplyMessage() {
        return replyMessage;
    }
}
