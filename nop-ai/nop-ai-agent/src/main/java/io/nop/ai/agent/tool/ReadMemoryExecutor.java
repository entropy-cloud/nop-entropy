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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Functional {@code read-memory} tool executor. Reads the current session's
 * memory via the per-session {@link IAiMemoryStore} resolved from
 * {@link AgentToolExecuteContext#getMemoryStore()}.
 *
 * <p>Supports four {@code action} modes:
 * <ul>
 *     <li>{@code list} (default): {@code getAll(filters)} — optional {@code type} filter</li>
 *     <li>{@code last}: {@code getLastN(n)} — required {@code n}</li>
 *     <li>{@code budgeted}: {@code readBudgeted(maxTokens, context)} — required {@code maxTokens}</li>
 *     <li>{@code key}: find a single item by key</li>
 * </ul>
 *
 * <p><b>Honest no-config reporting</b>: when the store is null (no memory
 * store provider wired), returns a descriptive error result rather than
 * pretending the memory is empty.
 */
public class ReadMemoryExecutor extends AbstractMemoryToolExecutor {
    public static final String TOOL_NAME = "read-memory";

    private static final Logger LOG = LoggerFactory.getLogger(ReadMemoryExecutor.class);

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
            String action = getStringArg(args, call, "action", "list");

            switch (action) {
                case "list":
                    return doList(call, store, args);
                case "last":
                    return doLast(call, store, args);
                case "budgeted":
                    return doBudgeted(call, store, args);
                case "key":
                    return doByKey(call, store, args);
                default:
                    return fail(call.getId(),
                            "read-memory failed: unknown action '" + action
                                    + "'. Supported actions: list, last, budgeted, key.");
            }
        } catch (Exception e) {
            LOG.error("read-memory failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doList(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        String typeFilter = getStringArg(args, call, "type");
        Map<String, Object> filters = new HashMap<>();
        if (typeFilter != null && !typeFilter.isEmpty()) {
            filters.put("type", typeFilter);
        }
        List<AiMemoryItem> items = store.getAll(filters);
        return CompletableFuture.completedFuture(formatItems(call, items, "list", typeFilter));
    }

    private CompletionStage<AiToolCallResult> doLast(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        int n = getIntArg(args, call, "n", 0);
        if (n <= 0) {
            return fail(call.getId(), "read-memory action=last requires a positive 'n' argument");
        }
        List<AiMemoryItem> items = store.getLastN(n);
        return CompletableFuture.completedFuture(formatItems(call, items, "last n=" + n, null));
    }

    private CompletionStage<AiToolCallResult> doBudgeted(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        int maxTokens = getIntArg(args, call, "maxTokens", 0);
        if (maxTokens <= 0) {
            return fail(call.getId(), "read-memory action=budgeted requires a positive 'maxTokens' argument");
        }
        List<AiMemoryItem> items = store.readBudgeted(maxTokens, new HashMap<>());
        return CompletableFuture.completedFuture(formatItems(call, items, "budgeted maxTokens=" + maxTokens, null));
    }

    private CompletionStage<AiToolCallResult> doByKey(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        String key = getStringArg(args, call, "key");
        if (key == null || key.isEmpty()) {
            return fail(call.getId(), "read-memory action=key requires a 'key' argument");
        }
        AiMemoryItem item = findByKey(store, key);
        if (item == null) {
            AiToolCallResult result = new AiToolCallResult();
            result.setId(call.getId());
            result.setStatus("success");
            AiToolOutput out = new AiToolOutput();
            out.setBody("No memory item found with key: " + key);
            result.setOutput(out);
            return CompletableFuture.completedFuture(result);
        }
        return CompletableFuture.completedFuture(formatItems(call, List.of(item), "key=" + key, null));
    }

    private static AiMemoryItem findByKey(IAiMemoryStore store, String key) {
        if (store instanceof io.nop.ai.agent.memory.InMemoryAiMemoryStore) {
            return ((io.nop.ai.agent.memory.InMemoryAiMemoryStore) store).findByKey(key);
        }
        return store.getAll(null).stream()
                .filter(i -> key.equals(i.getKey()))
                .findFirst()
                .orElse(null);
    }

    private AiToolCallResult formatItems(AiToolCall call, List<AiMemoryItem> items, String scope, String typeFilter) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        StringBuilder body = new StringBuilder();
        body.append("Memory (").append(scope).append("): ");
        if (typeFilter != null) {
            body.append("type=").append(typeFilter).append(", ");
        }
        body.append(items.size()).append(" item(s).\n");

        if (items.isEmpty()) {
            body.append("(no memory items)");
        } else {
            for (AiMemoryItem item : items) {
                body.append("- key=").append(item.getKey() != null ? item.getKey() : "<auto>");
                if (item.getType() != null) {
                    body.append(" type=").append(item.getType());
                }
                if (item.getPriority() != 0) {
                    body.append(" priority=").append(item.getPriority());
                }
                if (item.isPinned()) {
                    body.append(" [pinned]");
                }
                body.append('\n');
                body.append("  content: ").append(item.getContent() != null ? item.getContent() : "");
                body.append('\n');
            }
        }
        output.setBody(body.toString());
        result.setOutput(output);
        return result;
    }
}
