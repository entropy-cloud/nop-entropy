package io.nop.gpt.core.api;

import io.nop.api.core.beans.ExtensibleBean;

public class ChatOptions extends ExtensibleBean {
    private IChatProgressListener progressListener;

    public ChatOptions progressListener(IChatProgressListener progressListener) {
        this.setProgressListener(progressListener);
        return this;
    }

    public IChatProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(IChatProgressListener progressListener) {
        this.progressListener = progressListener;
    }
}
