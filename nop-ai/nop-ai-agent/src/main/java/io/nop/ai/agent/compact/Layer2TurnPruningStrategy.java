package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Layer 2 — intermediate turn pruning.
 * <p>
 * Prunes intermediate turns while preserving:
 * <ul>
 *   <li>head anchors — system message(s) and the first user goal</li>
 *   <li>a tail window — the last {@code keepTailPercent} fraction of messages</li>
 * </ul>
 * Tool_call ↔ tool_response boundary integrity is maintained by construction:
 * messages are grouped into turn-groups (an assistant message with tool_calls
 * stays together with all of its tool-response messages), and whole groups are
 * pruned — never a half-pair.
 * <p>
 * Independent trigger (design §7.2 correction): Layer 2 only acts when
 * {@code messageCount > triggerMaxMessages}. When too few messages (head/tail
 * overlap), it returns an explicit unchanged result and logs — never a silent
 * skip.
 * <p>
 * Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md}
 * §7.2 (Layer 2 row).
 */
public class Layer2TurnPruningStrategy implements ICompressionStrategy {

    public static final String NAME = "layer2-turn-pruning";

    private static final Logger LOG = LoggerFactory.getLogger(Layer2TurnPruningStrategy.class);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        ITokenHolder tokens = ITokenHolder.of(ctx);
        long tokensBefore = tokens.estimate(messages);

        if (messages == null || messages.isEmpty()) {
            return new CompactionResult(ctx.getSessionId(), 0, 0, 0, null, null);
        }

        CompactConfig config = ctx.getCompactConfig() != null
                ? ctx.getCompactConfig()
                : CompactConfig.defaults();

        if (messages.size() <= config.getTriggerMaxMessages()) {
            LOG.debug("Layer 2 skip: messageCount {} <= triggerMaxMessages {}, no pruning. session={}",
                    messages.size(), config.getTriggerMaxMessages(), ctx.getSessionId());
            return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                    messages.size(), null, null);
        }

        List<List<ChatMessage>> groups = groupIntoTurns(messages);

        int headEndIndex = computeHeadEndGroupIndex(groups);
        long keepTailMessages = Math.max(1, Math.round(Math.ceil(messages.size() * config.getKeepTailPercent())));
        int tailStartIndex = computeTailStartGroupIndex(groups, keepTailMessages);

        if (tailStartIndex <= headEndIndex) {
            LOG.info("Layer 2 skip: head/tail windows overlap (headEndGroup={}, tailStartGroup={}, totalGroups={}), "
                    + "too few messages to prune safely. session={}", headEndIndex, tailStartIndex, groups.size(),
                    ctx.getSessionId());
            return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                    messages.size(), null, null);
        }

        List<ChatMessage> pruned = new ArrayList<>();
        for (int i = 0; i < headEndIndex; i++) {
            pruned.addAll(groups.get(i));
        }
        for (int i = tailStartIndex; i < groups.size(); i++) {
            pruned.addAll(groups.get(i));
        }

        assertBoundaryIntegrity(pruned);

        long tokensAfter = tokens.estimate(pruned);
        LOG.info("Layer 2 pruned {} -> {} messages (tokens {} -> {}), session={}",
                messages.size(), pruned.size(), tokensBefore, tokensAfter, ctx.getSessionId());

        return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensAfter,
                pruned.size(), null, pruned);
    }

    /**
     * Group messages so that an assistant message with tool_calls stays together
     * with its immediately-following tool-response messages. A standalone
     * message (system, user, assistant without tool_calls) forms its own group.
     * This guarantees boundary integrity by construction.
     */
    static List<List<ChatMessage>> groupIntoTurns(List<ChatMessage> messages) {
        List<List<ChatMessage>> groups = new ArrayList<>();
        List<ChatMessage> current = null;
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatToolResponseMessage) {
                if (current == null) {
                    current = new ArrayList<>();
                    groups.add(current);
                }
                current.add(msg);
            } else {
                current = new ArrayList<>();
                current.add(msg);
                groups.add(current);
            }
        }
        return groups;
    }

    /**
     * Find the group index that ends the head region. The head must contain all
     * system messages and the first user message. Returns the exclusive end
     * index (groups[0..headEndIndex) are the head).
     */
    static int computeHeadEndGroupIndex(List<List<ChatMessage>> groups) {
        boolean seenFirstUser = false;
        int headEnd = 1;
        for (int gi = 0; gi < groups.size(); gi++) {
            for (ChatMessage msg : groups.get(gi)) {
                if (msg instanceof ChatUserMessage && !seenFirstUser) {
                    seenFirstUser = true;
                    headEnd = gi + 1;
                    break;
                }
            }
            if (seenFirstUser) {
                break;
            }
            headEnd = gi + 1;
        }
        if (!seenFirstUser && !groups.isEmpty()) {
            headEnd = groups.size();
        }
        return headEnd;
    }

    /**
     * Find the group index that starts the tail region, accumulating groups from
     * the end until the tail contains at least {@code keepTailMessages} messages.
     * Returns the inclusive start index (groups[tailStartIndex..end) are the tail).
     */
    static int computeTailStartGroupIndex(List<List<ChatMessage>> groups, long keepTailMessages) {
        long tailCount = 0;
        int tailStart = groups.size();
        for (int gi = groups.size() - 1; gi >= 0; gi--) {
            tailCount += groups.get(gi).size();
            tailStart = gi;
            if (tailCount >= keepTailMessages) {
                break;
            }
        }
        return tailStart;
    }

    /**
     * Defensive check: assert that no orphaned tool_call or tool_response exists
     * in the pruned result. Throws if boundary integrity was violated (this would
     * indicate a bug in the grouping logic).
     */
    static void assertBoundaryIntegrity(List<ChatMessage> messages) {
        Set<String> calledIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatAssistantMessage) {
                ChatAssistantMessage asm = (ChatAssistantMessage) msg;
                if (asm.getToolCalls() != null) {
                    for (io.nop.ai.api.chat.messages.ChatToolCall tc : asm.getToolCalls()) {
                        if (tc.getId() != null) {
                            calledIds.add(tc.getId());
                        }
                    }
                }
            }
        }
        Set<String> respondedIds = new HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatToolResponseMessage) {
                respondedIds.add(((ChatToolResponseMessage) msg).getToolCallId());
            }
        }
        if (!calledIds.equals(respondedIds)) {
            throw new IllegalStateException(
                    "Layer 2 boundary integrity violated: tool_call ids " + calledIds
                            + " do not match tool_response ids " + respondedIds);
        }
    }

    /**
     * Small helper to resolve the estimator once and reuse it, avoiding the
     * explicit NoOpContextCompactor coupling in the strategy body.
     */
    private interface ITokenHolder {
        long estimate(List<ChatMessage> messages);

        static ITokenHolder of(CompactionContext ctx) {
            ITokenEstimator estimator = NoOpContextCompactor.resolveEstimator(ctx);
            return estimator::estimateTokens;
        }
    }
}
