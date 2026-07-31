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
    int estimate(String text);

    int estimateForMessage(AiMessage message);
}
