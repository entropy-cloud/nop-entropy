package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.memory.AiMemoryItem;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Functional {@code search-memory} tool executor. Searches the current
 * session's memory via {@link IAiMemoryStore#search(String)}.
 *
 * <p>Required argument: {@code query} (search term — substring match on
 * content / type / key, case-insensitive for the in-memory implementation).
 *
 * <p><b>Honest no-config reporting</b>: when the store is null (no memory
 * store provider wired), returns a descriptive error result rather than
 * pretending the search returned no matches. When the store is present but
 * the search legitimately has no matches, returns a success result with an
 * empty list and a "no matches" note (this is a legitimate state, not an
 * error).
 */
public class SearchMemoryExecutor extends AbstractMemoryToolExecutor {
    public static final String TOOL_NAME = "search-memory";

    private static final Logger LOG = LoggerFactory.getLogger(SearchMemoryExecutor.class);

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            StoreResolution resolution = resolveStore(call, context);
            if (!resolution.ok) {
                return resolution.failureResult;
            }
            IAiMemoryStore store = resolution.store;

            Map<String, Object> args = resolveArguments(call);
            String query = getStringArg(args, call, "query");
            if (query == null || query.isEmpty()) {
                return fail(call.getId(), "search-memory failed: 'query' is required");
            }

            List<AiMemoryItem> matches = store.search(query);

            AiToolCallResult result = new AiToolCallResult();
            result.setId(call.getId());
            result.setStatus("success");
            AiToolOutput output = new AiToolOutput();
            StringBuilder body = new StringBuilder();
            body.append("Memory search (query='").append(query).append("'): ")
                    .append(matches.size()).append(" match(es).\n");

            if (matches.isEmpty()) {
                body.append("No matching memory items.");
            } else {
                for (AiMemoryItem item : matches) {
                    body.append("- key=").append(item.getKey() != null ? item.getKey() : "<auto>");
                    if (item.getType() != null) {
                        body.append(" type=").append(item.getType());
                    }
                    body.append('\n');
                    body.append("  content: ").append(item.getContent() != null ? item.getContent() : "");
                    body.append('\n');
                }
            }
            output.setBody(body.toString());
            result.setOutput(output);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            LOG.error("search-memory failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }
}
