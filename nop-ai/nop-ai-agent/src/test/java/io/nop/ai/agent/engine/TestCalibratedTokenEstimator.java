package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CalibratedTokenEstimator}.
 */
public class TestCalibratedTokenEstimator {

    private final ILlmDialect dialect = LlmDialectFactory.getDialect(ApiStyle.openai);

    private List<ChatMessage> messages(String... contents) {
        List<ChatMessage> list = new ArrayList<>();
        for (String c : contents) {
            list.add(new ChatUserMessage(c));
        }
        return list;
    }

    @Test
    void factorStartsAtOne() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        assertEquals(1.0, estimator.getFactor(), 0.0001);
    }

    @Test
    void calibratedEstimateEqualsBaseBeforeAnyData() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);
        long calibrated = estimator.estimateTokens(msgs);

        assertEquals(baseEstimate, calibrated,
                "Calibrated estimate should equal base estimate when factor is 1.0");
    }

    @Test
    void factorMovesTowardActualAfterOneResponse() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);
        int actualPromptTokens = (int) (baseEstimate * 2);

        estimator.record(msgs, actualPromptTokens);

        assertTrue(estimator.getFactor() > 1.0,
                "Factor should increase when actual > base estimate");
        assertTrue(estimator.getFactor() < 2.0,
                "Factor should not fully reach the ratio after one sample (EMA smoothing)");
    }

    @Test
    void factorConvergesWithRepeatedConsistentResponses() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);
        double targetRatio = 1.5;
        int actualPromptTokens = (int) (baseEstimate * targetRatio);

        for (int i = 0; i < 30; i++) {
            estimator.record(msgs, actualPromptTokens);
        }

        assertEquals(targetRatio, estimator.getFactor(), 0.05,
                "Factor should converge to the observed ratio after repeated samples");
    }

    @Test
    void singleOutlierIsDampenedByEma() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);

        estimator.record(msgs, (int) (baseEstimate * 10));

        assertTrue(estimator.getFactor() < 10.0,
                "A single outlier should not dominate — EMA dampens the jump");
    }

    @Test
    void factorClampedToMax() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);

        for (int i = 0; i < 100; i++) {
            estimator.record(msgs, (int) (baseEstimate * 100));
        }

        assertEquals(CalibratedTokenEstimator.MAX_FACTOR, estimator.getFactor(), 0.0001,
                "Factor should be clamped to MAX_FACTOR");
    }

    @Test
    void factorClampedToMin() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);

        for (int i = 0; i < 100; i++) {
            estimator.record(msgs, 1);
        }

        assertEquals(CalibratedTokenEstimator.MIN_FACTOR, estimator.getFactor(), 0.0001,
                "Factor should be clamped to MIN_FACTOR");
    }

    @Test
    void zeroPromptTokensSkipsUpdate() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        estimator.record(msgs, 0);

        assertEquals(1.0, estimator.getFactor(), 0.0001,
                "Zero promptTokens should not change the factor");
    }

    @Test
    void negativePromptTokensSkipsUpdate() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);

        estimator.record(messages("test"), -1);

        assertEquals(1.0, estimator.getFactor(), 0.0001,
                "Negative promptTokens should not change the factor");
    }

    @Test
    void emptyMessagesSkipsUpdate() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);

        estimator.record(Collections.emptyList(), 100);

        assertEquals(1.0, estimator.getFactor(), 0.0001,
                "Empty messages (base estimate <= 0) should not change the factor");
    }

    @Test
    void estimateUsesCalibratedFactor() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        List<ChatMessage> msgs = messages("hello world this is a test");

        long baseEstimate = dialect.estimateTokens(msgs);

        for (int i = 0; i < 30; i++) {
            estimator.record(msgs, (int) (baseEstimate * 2.0));
        }

        long calibrated = estimator.estimateTokens(msgs);
        long expected = Math.round(baseEstimate * estimator.getFactor());

        assertEquals(expected, calibrated,
                "Calibrated estimate should be base * factor");
        assertTrue(calibrated > baseEstimate,
                "Calibrated estimate should be greater than base after factor > 1.0");
    }

    @Test
    void nullMessagesReturnZero() {
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect, ApiStyle.openai);
        assertEquals(0, estimator.estimateTokens(null));
    }

    @Test
    void defaultInstanceHasFactorOne() {
        ITokenEstimator default1 = CalibratedTokenEstimator.defaultInstance();
        List<ChatMessage> msgs = messages("test message content");

        long baseEstimate = dialect.estimateTokens(msgs);
        long defaultEstimate = default1.estimateTokens(msgs);

        assertEquals(baseEstimate, defaultEstimate,
                "Default estimator should match base estimate (factor 1.0)");
    }
}
