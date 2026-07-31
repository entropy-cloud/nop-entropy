package io.nop.ai.core.service;

import io.nop.ai.core.AiCoreConfigs;
import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RATE_LIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MA6.3-AR-6 focused tests for the bounded {@code tryAcquire} rate limiting
 * in {@link ChatServiceImpl#checkRateLimit} and the aligned (deprecated)
 * {@link DefaultAiChatService} path:
 *
 * <ul>
 *   <li>under quota → passes</li>
 *   <li>quota exhausted → {@link ERR_AI_RATE_LIMITED} (carries
 *       {@code llmName} + {@code httpStatus=429} so
 *       {@code LlmErrorClassifier} sees RATE_LIMITED — retryable)</li>
 *   <li>the configured acquire timeout is actually passed to
 *       {@code IRateLimiter.tryAcquire} (bounded wait, no infinite block)</li>
 *   <li>no {@code rateLimit} config → limiter is skipped entirely</li>
 * </ul>
 *
 * <p>NOTE: {@code DefaultRateLimiter.getAcquireFailCount()} is NOT asserted
 * (known copy-paste bug in nop-kernel — platform protected area, out of
 * scope for this plan). Deterministic fake limiters are used instead of
 * wall-clock timing.
 */
public class TestChatServiceRateLimit {

    private static final String TEST_PROVIDER = "test-rate-limit-provider";

    /**
     * Deterministic stand-in for the token bucket: grants up to
     * {@code maxPermits} permits, then rejects. Records the timeout argument
     * passed to {@link #tryAcquire(int, long)} so the bounded-wait wiring is
     * verifiable without wall-clock sleeps.
     */
    static final class FakeRateLimiter implements IRateLimiter {
        final int maxPermits;
        int granted;
        long lastTimeout = -1;

        FakeRateLimiter(int maxPermits) {
            this.maxPermits = maxPermits;
        }

        @Override
        public boolean tryAcquire(int permits, long timeout) {
            this.lastTimeout = timeout;
            if (granted < maxPermits) {
                granted++;
                return true;
            }
            return false;
        }

        @Override
        public double getPermitsPerSecond() {
            return 0;
        }

        @Override
        public long getAcquireSuccessCount() {
            return granted;
        }

        @Override
        public long getAcquireFailCount() {
            return 0;
        }

        @Override
        public void resetStats() {
            granted = 0;
        }
    }

    /**
     * {@link ChatServiceImpl} with an injectable limiter factory so tests
     * never touch wall-clock timing or the HTTP stack.
     */
    static final class TestableChatService extends ChatServiceImpl {
        final FakeRateLimiter limiter;

        TestableChatService(FakeRateLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        protected IRateLimiter createRateLimiter(double rate) {
            return limiter;
        }
    }

    @AfterEach
    void restoreRateLimitTimeoutConfig() {
        AppConfig.getConfigProvider().updateConfigValue(
                AiCoreConfigs.CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT, 1000L);
    }

    private static LlmModel rateLimitedModel() {
        LlmModel model = new LlmModel();
        model.setRateLimit(1.0);
        return model;
    }

    @Test
    void rateLimitUnderQuotaPasses() {
        TestableChatService service = new TestableChatService(new FakeRateLimiter(5));
        LlmModel config = rateLimitedModel();
        for (int i = 0; i < 5; i++) {
            service.checkRateLimit(TEST_PROVIDER, config);
        }
        // No throw — all permits granted within quota.
    }

    @Test
    void rateLimitExhaustedThrowsRateLimitedError() {
        TestableChatService service = new TestableChatService(new FakeRateLimiter(1));
        LlmModel config = rateLimitedModel();

        service.checkRateLimit(TEST_PROVIDER, config);

        NopException error = assertThrows(NopException.class,
                () -> service.checkRateLimit(TEST_PROVIDER, config),
                "Second call past the quota must fail fast with ERR_AI_RATE_LIMITED");
        assertEquals(ERR_AI_RATE_LIMITED.getErrorCode(), error.getErrorCode(),
                "Failure shape must be the ERR_AI_RATE_LIMITED error code");
        assertEquals(TEST_PROVIDER, error.getParam(ARG_LLM_NAME),
                "Error must identify the rate-limited provider");
        assertEquals(429, error.getParam(ARG_HTTP_STATUS),
                "Error must carry httpStatus=429 so LlmErrorClassifier maps it to "
                        + "RATE_LIMITED (retryable) — never another 4xx (MA6.3-AR-6)");
    }

    @Test
    void rateLimitAcquireTimeoutIsPassedToTryAcquire() {
        // Configurable bounded wait: the configured timeout must reach the
        // limiter (bounded tryAcquire, not the old infinite acquire()).
        AppConfig.getConfigProvider().updateConfigValue(
                AiCoreConfigs.CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT, 500L);
        FakeRateLimiter limiter = new FakeRateLimiter(1);
        TestableChatService service = new TestableChatService(limiter);

        service.checkRateLimit(TEST_PROVIDER, rateLimitedModel());

        assertEquals(500L, limiter.lastTimeout,
                "The configured acquire timeout must be passed to tryAcquire (bounded wait)");
    }

    @Test
    void noRateLimitConfigSkipsLimiterEntirely() {
        TestableChatService service = new TestableChatService(new FakeRateLimiter(0));
        LlmModel config = new LlmModel(); // rateLimit == null
        service.checkRateLimit(TEST_PROVIDER, config);
        assertEquals(0, service.limiter.granted,
                "Without rateLimit config the limiter must never be consulted");
    }

    // ========================================================================
    // DefaultAiChatService (deprecated) alignment (MA6.3-AR-6)
    // ========================================================================

    /**
     * {@link DefaultAiChatService} with the model load and limiter factory
     * overridden so the rate-limit path is exercised without llm.xml or HTTP.
     */
    static final class TestableDefaultAiChatService extends DefaultAiChatService {
        final IRateLimiter limiter;

        TestableDefaultAiChatService(IRateLimiter limiter) {
            this.limiter = limiter;
        }

        @Override
        protected IRateLimiter getRateLimiter(String llmName) {
            return limiter;
        }

        @Override
        protected LlmModel loadLlmModel(String llmName) {
            return rateLimitedModel();
        }
    }

    @Test
    void defaultAiChatServiceRateLimitExhaustedFailsFast() {
        TestableDefaultAiChatService service =
                new TestableDefaultAiChatService(new FakeRateLimiter(0));
        AiChatOptions options = new AiChatOptions();
        options.setProvider(TEST_PROVIDER);
        options.setModel("test-model");

        NopException error = assertThrows(NopException.class,
                () -> service.sendChatAsync(Prompt.userText("hi"), options, null),
                "Deprecated DefaultAiChatService must align: bounded tryAcquire + ERR_AI_RATE_LIMITED");
        assertEquals(ERR_AI_RATE_LIMITED.getErrorCode(), error.getErrorCode());
        assertEquals(429, error.getParam(ARG_HTTP_STATUS));
    }

    @Test
    void chatServiceErrorCodeParamKeysMatchClassifierContract() {
        // Contract guard (MA6.3-AR-6): the ARG_* param keys used by
        // ChatServiceImpl/DefaultAiChatService are exactly the keys
        // LlmErrorClassifier.readHttpStatus reads (nop-ai-agent). A rename in
        // one side breaks the retryable classification — this test pins the
        // key constants so the contract drift is caught at compile time.
        assertEquals("httpStatus", ARG_HTTP_STATUS);
        assertEquals("llmName", ARG_LLM_NAME);
    }
}
