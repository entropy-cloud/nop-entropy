package io.nop.ai.core.reliability;

import io.nop.ai.api.chat.ChatOptions;

/**
 * Composite model identity key helpers (W2 reliability sink-down, plan
 * 2026-08-15-0604-2): {@link #buildModelKey} moved verbatim from the former
 * {@code LlmCallCoordinator.buildModelKey} so nop-ai-core consumers (circuit
 * breaker, account chain, provider failover) do not depend on nop-ai-agent.
 */
public final class ModelKeys {

    private ModelKeys() {
    }

    /**
     * Build the composite model identity key ({@code provider:model}) from a
     * {@link ChatOptions} instance, as returned by {@code RoutingResult.getOptions()}.
     * Null provider/model are normalized to empty strings so the key is always
     * non-null and comparable. This is the model identity used to detect
     * switches between ReAct iterations.
     */
    public static String buildModelKey(ChatOptions options) {
        String provider = options.getProvider() != null ? options.getProvider() : "";
        String model = options.getModel() != null ? options.getModel() : "";
        return provider + ":" + model;
    }
}
