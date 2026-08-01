package io.nop.ai.core.api.tokenizer;

import io.nop.ai.core.api.messages.AiMessage;

/**
 * Token count estimator SPI extension contract (adjudicated per MA5.1 P1-01 / arm-index P1-MA5-003).
 * <p>
 * This is an SPI extension point: the platform ships no production implementation by
 * design — integrators provide concrete implementations tuned to their LLM tokenizer.
 * The agent engine token estimation uses {@code ITokenEstimator} (agent layer) instead;
 * consumers of this interface must inject a concrete implementation and fail fast at
 * wiring time when none is registered.
 */
public interface ITokenCountEstimator {
    /**
     * Estimates the token count of the given plain text.
     *
     * @param text the text to estimate (may be empty)
     * @return the estimated token count (>= 0)
     */
    int estimate(String text);

    /**
     * Estimates the token count of a legacy AI message, including per-message overhead.
     *
     * @param message the legacy AI message to estimate
     * @return the estimated token count (>= 0)
     */
    int estimateForMessage(AiMessage message);
}
