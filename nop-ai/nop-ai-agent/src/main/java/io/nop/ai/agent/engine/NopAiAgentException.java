package io.nop.ai.agent.engine;

public class NopAiAgentException extends RuntimeException {

    public NopAiAgentException(String message) {
        super(message);
    }

    public NopAiAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
