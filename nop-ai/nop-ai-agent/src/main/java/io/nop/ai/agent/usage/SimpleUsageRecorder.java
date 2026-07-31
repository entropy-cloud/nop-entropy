package io.nop.ai.agent.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured-log {@link IUsageRecorder} (MA6.3-AR-4): emits one SLF4J INFO
 * line per {@link #record(UsageRecord)} call, so per-LLM-call usage is
 * observable out-of-the-box without a persistence sink.
 *
 * <p>Wiring: register via
 * {@code DefaultAgentEngine.Builder#usageRecorder(IUsageRecorder)} /
 * {@code DefaultAgentEngine.setUsageRecorder(IUsageRecorder)}. Unlike the
 * pass-through {@link NoOpUsageRecorder} default, this recorder makes token
 * metering visible in the application log; a production recorder (e.g.
 * {@code DbUsageRecorder} writing {@code NopAiChatResponse}, L2-18) persists
 * usage for billing. Structured log lines are parseable by log collectors
 * (pattern: {@code nop.ai.agent.usage-record: key=value, ...}).
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public class SimpleUsageRecorder implements IUsageRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleUsageRecorder.class);

    @Override
    public void record(UsageRecord record) {
        if (record == null) {
            LOG.warn("nop.ai.agent.usage-record: record is null — skipping");
            return;
        }
        LOG.info("nop.ai.agent.usage-record: sessionId={}, agentName={}, requestId={}, "
                        + "aiProvider={}, aiModel={}, modelId={}, promptTokens={}, "
                        + "completionTokens={}, responseDurationMs={}, responseTimestamp={}",
                record.getSessionId(), record.getAgentName(), record.getRequestId(),
                record.getAiProvider(), record.getAiModel(), record.getModelId(),
                record.getPromptTokens(), record.getCompletionTokens(),
                record.getResponseDurationMs(), record.getResponseTimestamp());
    }
}
