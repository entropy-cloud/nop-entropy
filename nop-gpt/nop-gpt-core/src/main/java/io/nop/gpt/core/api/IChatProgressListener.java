package io.nop.gpt.core.api;

public interface IChatProgressListener {
    void onRecvMessage(String message);
}
