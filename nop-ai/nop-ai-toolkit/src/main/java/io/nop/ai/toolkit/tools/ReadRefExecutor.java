package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.ICompactionArchiveReader;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.compact.ShortRefHasher;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

/**
 * {@code read-ref} tool: reads back original content that was replaced by a
 * {@code shortRef} pointer during reference-style compaction (design §8.2).
 * <p>
 * The LLM encounters a {@code [SHORT_REF type=... path=... range=...
 * hash=sha256:&lt;hex&gt;]} marker in the (compacted) message history and calls
 * this tool with the marker's fields. The tool:
 * <ol>
 *   <li>reads the archived original content via
 *       {@link ICompactionArchiveReader#getByHash(String)} (exposed by
 *       {@link IToolExecuteContext#getCompactionArchiveReader()})</li>
 *   <li>re-computes the SHA-256 of the read-back content and compares it to
 *       the {@code hash} argument — content-addressed integrity check</li>
 *   <li>on match: returns the original content as a successful result</li>
 *   <li>on mismatch or missing reference: returns an <b>explicit error</b>
 *       result ("content changed / reference invalid, please re-read") —
 *       fail-loud, never returns empty/stale content (Minimum Rules #24)</li>
 * </ol>
 *
 * <p><b>Tool visibility</b> (context-model §4.1): this tool only accesses the
 * archive's read-only view. It does NOT read the full message history, the
 * Plan, or any other tool's internal state — same visibility contract as
 * {@code read-file}.
 *
 * <p><b>Outside an agent session</b>: when
 * {@link IToolExecuteContext#getCompactionArchiveReader()} returns
 * {@code null} (no archive materialised — no reference-style compaction has
 * run) or throws {@link UnsupportedOperationException} (the context is not
 * the agent engine's), the tool returns an explicit error result rather than
 * silently returning empty content.
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.2 (Decision C read-side access, Decision E input contract, Minimum
 * Rules #24 fail-loud).
 */
public class ReadRefExecutor implements IToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ReadRefExecutor.class);

    public static final String TOOL_NAME = "read-ref";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(call, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        int callId = call.getId();
        String hash = call.attrText("hash", "");
        if (hash.isEmpty()) {
            return AiToolCallResult.errorResult(callId,
                    "read-ref requires a 'hash' argument (the shortRef read-back key)");
        }

        ICompactionArchiveReader reader;
        try {
            reader = context.getCompactionArchiveReader();
        } catch (UnsupportedOperationException e) {
            // Default UOE bridge: read-ref invoked outside an agent engine
            // (no archive available). Fail-loud with a descriptive error
            // rather than silently returning empty content.
            LOG.warn("read-ref invoked outside an agent engine: no compaction archive available", e);
            return AiToolCallResult.errorResult(callId,
                    "read-ref is not available in this context: no compaction archive "
                            + "(read-ref requires the agent engine's AgentToolExecuteContext).");
        }
        if (reader == null) {
            // AgentToolExecuteContext override returns null when no archive
            // has been materialised yet (no reference-style compaction has
            // run). Fail-loud: there is nothing to read back.
            return AiToolCallResult.errorResult(callId,
                    "read-ref: no compaction archive is available for this session "
                            + "(no reference-style compaction has run, so there is nothing to read back).");
        }

        String archived = reader.getByHash(hash);
        if (archived == null) {
            // Reference invalid: the hash is not in the archive. Either the
            // reference was never archived in this session, the session was
            // restarted (in-memory archive lost), or the hash is malformed.
            // Fail-loud — do NOT return empty/stale content.
            return AiToolCallResult.errorResult(callId,
                    "read-ref: reference invalid — no archived content for hash " + hash
                            + ". The content may have been evicted, the session restarted, "
                            + "or the hash is incorrect. Please re-read the original source.");
        }

        // Content-addressed integrity check: re-compute the hash of the
        // read-back content and compare to the requested hash. A mismatch
        // means the archived content was corrupted/substituted — fail-loud.
        if (!ShortRefHasher.verify(archived, hash)) {
            return AiToolCallResult.errorResult(callId,
                    "read-ref: content integrity check FAILED for hash " + hash
                            + ". The archived content's hash does not match the requested hash "
                            + "(content may have been corrupted or substituted). "
                            + "Do NOT trust this content — please re-read the original source.");
        }

        return AiToolCallResult.successResult(callId, archived);
    }
}
