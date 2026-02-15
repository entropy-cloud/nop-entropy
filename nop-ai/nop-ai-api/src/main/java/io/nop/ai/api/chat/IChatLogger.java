package io.nop.ai.api.chat;

public interface IChatLogger {
    void logRequest(ChatRequest request);

    void logResponse(ChatRequest request, ChatResponse response);
}