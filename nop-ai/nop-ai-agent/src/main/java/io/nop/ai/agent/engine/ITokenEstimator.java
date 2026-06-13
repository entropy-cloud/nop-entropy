package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

/**
 * Token estimation capability with runtime calibration.
 * <p>
 * Provides a pre-call token estimate for a list of chat messages.
 * The estimate is refined over time by feeding actual Provider usage
 * ({@code ChatUsage.promptTokens}) via {@link #record}.
 *
 * @see CalibratedTokenEstimator
 */
public interface ITokenEstimator {

    /**
     * Estimate the token count for the given messages.
     *
     * @param messages the messages to estimate, may be null or empty
     * @return estimated token count (always {@code >= 0})
     */
    long estimateTokens(List<ChatMessage> messages);

    /**
     * Feed actual Provider usage to refine future estimates.
     * <p>
     * If {@code actualPromptTokens <= 0} or the base estimate is non-positive,
     * the call is a no-op (explicit skip — nothing to learn).
     *
     * @param messagesSent       the messages that were sent in the request
     * @param actualPromptTokens the actual prompt token count returned by the Provider
     */
    void record(List<ChatMessage> messagesSent, int actualPromptTokens);
}
