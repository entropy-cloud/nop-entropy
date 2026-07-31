package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.model.ApiStyle;

import java.util.List;

/**
 * Runtime-calibrated token estimator.
 * <p>
 * Wraps an {@link ILlmDialect}'s baseline {@code estimateTokens()} and refines
 * it using exponential moving average (EMA) smoothing of the ratio between
 * actual Provider usage and the baseline estimate.
 * <p>
 * Calibration algorithm (per instance, keyed on the ApiStyle provided at construction):
 * <ol>
 *   <li>factor starts at {@code 1.0}</li>
 *   <li>on each response with positive {@code promptTokens}:
 *     {@code observedRatio = actual / baseEstimate}</li>
 *   <li>EMA update: {@code factor = factor * (1 - alpha) + observedRatio * alpha}</li>
 *   <li>clamp factor to {@code [MIN_FACTOR, MAX_FACTOR]}</li>
 *   <li>skip when {@code baseEstimate <= 0} (nothing to learn)</li>
 * </ol>
 * Calibrated estimate = {@code round(dialect.estimateTokens(messages) * factor)}.
 *
 * <p><b>Error claim (MA6.3-AR-3)</b>: {@code MAX_FACTOR = 4.0} is an EMA clamp
 * cap, NOT an accuracy guarantee. The "≤ 4×" error bound holds only AFTER the
 * EMA has converged to the deployment's real prompt-token ratio (enough
 * observed responses). An uncalibrated instance (factor = 1.0) can undercount
 * far beyond 4× (the {@code chars/4} baseline heuristic is English-optimized;
 * CJK text and tokenizer-dependent models deviate the most). Compaction
 * triggers consuming this estimate should keep a conservative margin until
 * per-deployment calibration has converged, and should monitor
 * {@link #getFactor()} (e.g. log it at startup / periodically) to detect
 * non-convergence.
 */
public class CalibratedTokenEstimator implements ITokenEstimator {

    public static final double SMOOTHING_ALPHA = 0.3;
    public static final double MIN_FACTOR = 0.25;
    public static final double MAX_FACTOR = 4.0;

    private final ILlmDialect dialect;
    private final ApiStyle apiStyle;

    private volatile double factor = 1.0;

    public CalibratedTokenEstimator(ILlmDialect dialect, ApiStyle apiStyle) {
        if (dialect == null) {
            throw new NopAiAgentException("dialect must not be null");
        }
        this.dialect = dialect;
        this.apiStyle = apiStyle != null ? apiStyle : ApiStyle.openai;
    }

    public ApiStyle getApiStyle() {
        return apiStyle;
    }

    public double getFactor() {
        return factor;
    }

    @Override
    public long estimateTokens(List<ChatMessage> messages) {
        long base = dialect.estimateTokens(messages);
        if (base <= 0) {
            return 0;
        }
        return Math.round(base * factor);
    }

    @Override
    public void record(List<ChatMessage> messagesSent, int actualPromptTokens) {
        if (actualPromptTokens <= 0) {
            return;
        }
        long base = dialect.estimateTokens(messagesSent);
        if (base <= 0) {
            return;
        }
        double observedRatio = (double) actualPromptTokens / (double) base;
        double newFactor = factor * (1.0 - SMOOTHING_ALPHA) + observedRatio * SMOOTHING_ALPHA;
        newFactor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, newFactor));
        factor = newFactor;
    }
}
