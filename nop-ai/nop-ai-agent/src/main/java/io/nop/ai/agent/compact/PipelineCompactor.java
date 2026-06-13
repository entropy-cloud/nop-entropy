package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.core.model.ChatOptionsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pipeline orchestrator that runs the configured {@link ICompressionStrategy}
 * instances in escalation order (Layer 1 -> Layer 2 -> Layer 3).
 * <p>
 * After each layer it re-estimates tokens via the injected estimator. If the
 * context falls below the trigger threshold ({@code maxContextTokens *
 * triggerTokenPercent} AND {@code messageCount <= triggerMaxMessages}),
 * escalation stops — the layer "relieved" the context.
 * <p>
 * Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md}
 * §7.1-§7.3. Layer 0 (tool-result pre-truncation) stays in the executor and is
 * not part of this pipeline.
 * <p>
 * This class never fails the agent: when no strategies are configured an
 * explicit NoOp-equivalent reporting result is returned; empty/null message
 * lists are handled explicitly.
 */
public class PipelineCompactor implements IContextCompactor {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineCompactor.class);

    private final List<ICompressionStrategy> strategies;

    public PipelineCompactor(List<ICompressionStrategy> strategies) {
        this.strategies = strategies != null
                ? Collections.unmodifiableList(new ArrayList<>(strategies))
                : Collections.emptyList();
    }

    public PipelineCompactor(ICompressionStrategy... strategies) {
        this(strategies != null ? Arrays.asList(strategies) : Collections.emptyList());
    }

    public List<ICompressionStrategy> getStrategies() {
        return strategies;
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        List<ChatMessage> messages = ctx.getMessages();

        if (messages == null || messages.isEmpty()) {
            return new CompactionResult(ctx.getSessionId(), 0, 0, 0, null, null);
        }

        if (strategies.isEmpty()) {
            LOG.debug("PipelineCompactor has no strategies configured, returning NoOp-equivalent result for session {}",
                    ctx.getSessionId());
            return NoOpContextCompactor.INSTANCE.compact(ctx);
        }

        ITokenEstimator estimator = NoOpContextCompactor.resolveEstimator(ctx);
        long tokensBefore = estimator.estimateTokens(messages);

        long maxContextTokens = resolveMaxContextTokens(ctx);
        CompactConfig config = ctx.getCompactConfig() != null
                ? ctx.getCompactConfig()
                : CompactConfig.defaults();
        long tokenThreshold = (long) (maxContextTokens * config.getTriggerTokenPercent());
        int messageThreshold = config.getTriggerMaxMessages();

        List<ChatMessage> current = new ArrayList<>(messages);
        long currentTokens = tokensBefore;

        for (ICompressionStrategy strategy : strategies) {
            if (isRelieved(currentTokens, current.size(), tokenThreshold, messageThreshold)) {
                LOG.debug("Pipeline context relieved before layer {}: tokens={}, messages={}, session={}",
                        strategy.name(), currentTokens, current.size(), ctx.getSessionId());
                break;
            }

            CompactionContext layerCtx = rebuildContext(ctx, current, config);
            CompactionResult layerResult;
            try {
                layerResult = strategy.compact(layerCtx);
            } catch (Exception e) {
                LOG.warn("Compression strategy {} threw exception, skipping layer (agent continues unchanged). session={}",
                        strategy.name(), ctx.getSessionId(), e);
                continue;
            }

            if (layerResult == null) {
                LOG.warn("Compression strategy {} returned null result, skipping layer. session={}",
                        strategy.name(), ctx.getSessionId());
                continue;
            }

            if (layerResult.getCompactedMessages() != null
                    && !layerResult.getCompactedMessages().isEmpty()
                    && layerResult.getTokensAfter() < currentTokens) {
                current = new ArrayList<>(layerResult.getCompactedMessages());
                currentTokens = estimator.estimateTokens(current);
                LOG.info("Layer {} reduced tokens {} -> {}, messages {} for session {}",
                        strategy.name(), tokensBefore, currentTokens, current.size(), ctx.getSessionId());
            } else {
                LOG.debug("Layer {} did not relieve context (tokensAfter={}, currentTokens={}), continuing escalation. session={}",
                        strategy.name(), layerResult.getTokensAfter(), currentTokens, ctx.getSessionId());
            }
        }

        if (currentTokens < tokensBefore) {
            return new CompactionResult(ctx.getSessionId(), tokensBefore, currentTokens,
                    current.size(), null, current);
        }
        return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                messages.size(), null, null);
    }

    /**
     * Relief check: escalation stops only when BOTH trigger dimensions are back
     * within bounds (token estimate below threshold AND message count at or below
     * threshold). Matches the dual-dimension OR-gate trigger in design §7.3.
     */
    static boolean isRelieved(long currentTokens, int messageCount,
                              long tokenThreshold, int messageThreshold) {
        return currentTokens <= tokenThreshold && messageCount <= messageThreshold;
    }

    private CompactionContext rebuildContext(CompactionContext ctx, List<ChatMessage> currentMessages, CompactConfig config) {
        return new CompactionContext(
                currentMessages,
                config,
                ctx.getSessionId(),
                ctx.getAgentName(),
                ctx.getExecutionContext(),
                ctx.getTokenEstimator()
        );
    }

    private long resolveMaxContextTokens(CompactionContext ctx) {
        AgentExecutionContext execCtx = ctx.getExecutionContext();
        if (execCtx != null) {
            ChatOptionsModel chatOptions = execCtx.getChatOptionsModel();
            if (chatOptions != null && chatOptions.getMaxTokens() != null) {
                return chatOptions.getMaxTokens();
            }
        }
        return ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS;
    }
}
