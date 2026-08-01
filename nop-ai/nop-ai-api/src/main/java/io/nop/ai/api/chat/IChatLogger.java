package io.nop.ai.api.chat;

/**
 * Logging hook for chat requests and responses.
 * <p>
 * Implementations record the traffic sent to and received from the LLM provider,
 * typically for debugging or auditing. The default implementation
 * ({@code DefaultChatLogger} in nop-ai-core) writes request/response payloads to
 * SLF4J and optionally to files, redacting credentials when configured.
 */
public interface IChatLogger {
    /**
     * @param request the chat request to log
     */
    void logRequest(ChatRequest request);

    /**
     * @param request  the chat request that produced the response
     * @param response the chat response to log
     */
    void logResponse(ChatRequest request, ChatResponse response);
}