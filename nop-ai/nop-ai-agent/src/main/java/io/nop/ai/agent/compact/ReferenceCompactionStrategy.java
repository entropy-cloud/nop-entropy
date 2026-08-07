package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.toolkit.api.ICompactionArchive;
import io.nop.ai.toolkit.compact.ShortRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reference-style compaction strategy (design §8.2). Lossless pointer path
 * complementing the summary-style layers: replaces long, referenceable
 * tool-response content with a {@code shortRef} pointer and archives the
 * original in the per-session archive (content-addressed by SHA-256 hash).
 * The LLM later uses the pointer to call {@code read-ref} and read back the
 * original content.
 *
 * <p><b>Content-type routing</b> (Decision A): only {@code tool-response}
 * messages produced by a referenceable tool (file/grep/glob/search family —
 * see {@link ShortRef#typeForTool(String)}) AND whose content exceeds the
 * configured length threshold enter the reference candidate pool.
 * Conversation/reasoning messages are never referenced (they belong to the
 * summary path).
 *
 * <p><b>Recency protection</b>: the most recent {@code N} tool responses
 * (default = {@code CompactConfig.maxRecentToolResults}) are kept verbatim —
 * the LLM is actively using them, so replacing them with pointers would
 * hurt the active turn. Only older tool responses are reference-ified.
 *
 * <p><b>Explicit unchanged</b> (Decision F + Minimum Rules #24): when no
 * archive is wired, no referenceable content exists, or nothing was
 * reference-ified, returns an explicit unchanged result
 * ({@code tokensAfter == tokensBefore} + {@code compactedMessages == null})
 * — never throws, never silently swallows content. Archive {@code put}
 * failures propagate as exceptions (the {@link PipelineCompactor} wraps each
 * strategy call in try/catch and skips the layer on failure, so a put
 * failure degrades gracefully to "this layer did not help").
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.2 (Decision A signal source, Decision E shortRef format, Decision F
 * escalation ordering, Decision G write-side wiring).
 */
public class ReferenceCompactionStrategy implements ICompressionStrategy {

    public static final String NAME = "reference-compaction";

    /**
     * Default minimum content length (in characters) below which a tool
     * response is NOT worth reference-ifying — short results cost more as a
     * pointer than as inline content. Tuned to roughly match the
     * {@code shortRef} pointer overhead + a margin so that very short
     * results stay inline.
     */
    public static final int DEFAULT_REFERENCE_THRESHOLD_CHARS = 2000;

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceCompactionStrategy.class);

    private final int referenceThresholdChars;

    public ReferenceCompactionStrategy() {
        this(DEFAULT_REFERENCE_THRESHOLD_CHARS);
    }

    public ReferenceCompactionStrategy(int referenceThresholdChars) {
        this.referenceThresholdChars = referenceThresholdChars > 0
                ? referenceThresholdChars
                : DEFAULT_REFERENCE_THRESHOLD_CHARS;
    }

    @Override
    public String name() {
        return NAME;
    }

    int getReferenceThresholdChars() {
        return referenceThresholdChars;
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        ITokenEstimator estimator = NoOpContextCompactor.resolveEstimator(ctx);
        long tokensBefore = estimator.estimateTokens(messages);

        if (messages == null || messages.isEmpty()) {
            return new CompactionResult(ctx.getSessionId(), 0, 0, 0, null, null);
        }

        ICompactionArchive archive = ctx.getCompactionArchive();
        if (archive == null) {
            // No archive wired (e.g. coordinator could not resolve a session).
            // Explicit unchanged — do NOT throw, do NOT silently produce
            // pointers with nowhere to PUT the original (that would lose
            // content). The pipeline escalates to the next layer.
            LOG.debug("Reference compaction skip: no archive wired for session {}", ctx.getSessionId());
            return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                    messages.size(), null, null);
        }

        int maxRecent = ctx.getCompactConfig() != null
                ? ctx.getCompactConfig().getMaxRecentToolResults()
                : CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS;
        Set<String> recentToolCallIds = collectRecentToolCallIds(messages, maxRecent);

        List<ChatMessage> compactedMessages = new ArrayList<>(messages.size());
        boolean anyReferenced = false;

        for (ChatMessage msg : messages) {
            ChatMessage replacement = maybeReference(msg, archive, recentToolCallIds, ctx.getSessionId());
            if (replacement != null) {
                compactedMessages.add(replacement);
                anyReferenced = true;
            } else {
                compactedMessages.add(msg);
            }
        }

        if (!anyReferenced) {
            // Explicit unchanged: no content matched the reference candidate
            // criteria (all tool responses were either recent, from
            // non-referenceable tools, or below the length threshold). Do not
            // throw, do not silently produce an empty result.
            LOG.debug("Reference compaction skip: no referenceable content for session {}", ctx.getSessionId());
            return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                    messages.size(), null, null);
        }

        long tokensAfter = estimator.estimateTokens(compactedMessages);
        LOG.info("Reference compaction: archived long tool responses, tokens {} -> {}, messages {} for session {}",
                tokensBefore, tokensAfter, compactedMessages.size(), ctx.getSessionId());
        return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensAfter,
                compactedMessages.size(), null, compactedMessages);
    }

    /**
     * If {@code msg} is a reference candidate (tool-response from a
     * referenceable tool, not recent, content above threshold), PUT its
     * original content into {@code archive} and return a copy whose content
     * is replaced by the {@code shortRef} pointer. Returns {@code null} when
     * the message is not a reference candidate (caller keeps the original).
     */
    private ChatMessage maybeReference(ChatMessage msg, ICompactionArchive archive,
                                       Set<String> recentToolCallIds, String sessionId) {
        if (!(msg instanceof ChatToolResponseMessage)) {
            return null;
        }
        ChatToolResponseMessage toolResp = (ChatToolResponseMessage) msg;
        String toolCallId = toolResp.getCallId();
        if (toolCallId != null && recentToolCallIds.contains(toolCallId)) {
            return null;
        }
        String content = toolResp.getContent();
        if (content == null || content.length() < referenceThresholdChars) {
            return null;
        }
        String toolName = toolResp.getName();
        String type = ShortRef.typeForTool(toolName);
        if (type == null) {
            return null;
        }

        // PUT the original content into the per-session archive. The archive
        // computes the SHA-256 hash and returns the read-back key. A put
        // failure (e.g. null/empty content rejected by the archive) throws
        // — the PipelineCompactor catches per-strategy exceptions and skips
        // the layer, so we degrade gracefully without losing the message.
        String hash = archive.put(content);

        // Reconstruct path/range hints from the original message if present.
        // These are best-effort locating hints — the read-back key is the hash.
        String path = extractPathHint(toolResp);
        String range = null;

        ShortRef shortRef = new ShortRef(type, path, range, hash);
        ChatToolResponseMessage replacement = toolResp.copy();
        replacement.setContent(shortRef.serialize());
        LOG.debug("Reference compaction: archived tool response toolCallId={} toolName={} hash={} chars={} for session {}",
                toolCallId, toolName, hash, content.length(), sessionId);
        return replacement;
    }

    /**
     * Best-effort path hint extraction. The referenceable tools (read-file,
     * grep, glob, search) include the path in their structured
     * {@code AiToolOutput} (set by the executor). The tool-response message
     * carries it in {@code result} (a JSON object) when present. We do not
     * parse the JSON here — the path is a locating hint, not a read-back key.
     * Returns {@code null} when no path hint is recoverable.
     */
    private static String extractPathHint(ChatToolResponseMessage toolResp) {
        Object resultObj = toolResp.getResult();
        if (resultObj instanceof io.nop.ai.toolkit.model.AiToolOutput) {
            String p = ((io.nop.ai.toolkit.model.AiToolOutput) resultObj).getPath();
            if (p != null && !p.isEmpty()) {
                return p;
            }
        }
        return null;
    }

    /**
     * Collect the {@code maxRecent} most recent tool-response toolCallIds so
     * they are kept verbatim (the active turn's tool results). Mirrors the
     * pattern in {@code MicroCompressionCompactor.collectRecentToolCallIds}.
     */
    private static Set<String> collectRecentToolCallIds(List<ChatMessage> messages, int maxRecent) {
        List<String> recentIds = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && recentIds.size() < maxRecent; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatToolResponseMessage) {
                String id = ((ChatToolResponseMessage) msg).getCallId();
                if (id != null) {
                    recentIds.add(id);
                }
            }
        }
        return new HashSet<>(recentIds);
    }
}
