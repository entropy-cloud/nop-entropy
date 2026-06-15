package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.memory.AiMemoryItem;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.InMemoryAiMemoryStore;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Functional {@code write-memory} tool executor. Writes / updates / removes
 * items in the current session's memory via the per-session
 * {@link IAiMemoryStore} resolved from
 * {@link AgentToolExecuteContext#getMemoryStore()}.
 *
 * <p>Supports four {@code action} modes:
 * <ul>
 *     <li>{@code add} (default): {@code add(item)} — required {@code content};
 *         optional {@code key} / {@code type} / {@code priority} / {@code pinned}</li>
 *     <li>{@code update}: {@code update(key, item)} — required {@code key} + {@code content}</li>
 *     <li>{@code remove}: {@code remove(key)} — required {@code key}</li>
 *     <li>{@code clear}: remove all items (no args)</li>
 * </ul>
 *
 * <p><b>Honest no-config reporting</b>: when the store is null (no memory
 * store provider wired), returns a descriptive error result rather than
 * pretending the write succeeded.
 */
public class WriteMemoryExecutor extends AbstractMemoryToolExecutor {
    public static final String TOOL_NAME = "write-memory";

    private static final Logger LOG = LoggerFactory.getLogger(WriteMemoryExecutor.class);

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
            String action = getStringArg(args, call, "action", "add");

            switch (action) {
                case "add":
                    return doAdd(call, store, args);
                case "update":
                    return doUpdate(call, store, args);
                case "remove":
                    return doRemove(call, store, args);
                case "clear":
                    return doClear(call, store);
                default:
                    return fail(call.getId(),
                            "write-memory failed: unknown action '" + action
                                    + "'. Supported actions: add, update, remove, clear.");
            }
        } catch (Exception e) {
            LOG.error("write-memory failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doAdd(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        String content = getStringArg(args, call, "content");
        if (content == null) {
            // allow `input` as a fallback alias for content
            content = getStringArg(args, call, "input");
        }
        if (content == null || content.isEmpty()) {
            return fail(call.getId(), "write-memory action=add requires a 'content' argument");
        }

        AiMemoryItem item = new AiMemoryItem();
        item.setContent(content);
        String key = getStringArg(args, call, "key");
        if (key != null && !key.isEmpty()) {
            item.setKey(key);
        }
        String type = getStringArg(args, call, "type");
        if (type != null && !type.isEmpty()) {
            item.setType(type);
        }
        item.setPriority(getIntArg(args, call, "priority", 0));
        item.setPinned(getBooleanArg(args, call, "pinned", false));

        store.add(item);

        int remaining = countItems(store);
        return CompletableFuture.completedFuture(successSummary(call, "Added memory item"
                + (item.getKey() != null ? " (key=" + item.getKey() + ")" : "")
                + ". Total items in store: " + remaining));
    }

    private CompletionStage<AiToolCallResult> doUpdate(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        String key = getStringArg(args, call, "key");
        if (key == null || key.isEmpty()) {
            return fail(call.getId(), "write-memory action=update requires a 'key' argument");
        }
        String content = getStringArg(args, call, "content");
        if (content == null) {
            content = getStringArg(args, call, "input");
        }

        AiMemoryItem item = new AiMemoryItem();
        item.setContent(content != null ? content : "");
        String type = getStringArg(args, call, "type");
        if (type != null && !type.isEmpty()) {
            item.setType(type);
        }
        item.setPriority(getIntArg(args, call, "priority", 0));
        item.setPinned(getBooleanArg(args, call, "pinned", false));

        store.update(key, item);

        int remaining = countItems(store);
        return CompletableFuture.completedFuture(successSummary(call, "Updated memory item (key=" + key + "). Total items in store: " + remaining));
    }

    private CompletionStage<AiToolCallResult> doRemove(AiToolCall call, IAiMemoryStore store, Map<String, Object> args) {
        String key = getStringArg(args, call, "key");
        if (key == null || key.isEmpty()) {
            return fail(call.getId(), "write-memory action=remove requires a 'key' argument");
        }
        store.remove(key);

        int remaining = countItems(store);
        return CompletableFuture.completedFuture(successSummary(call, "Removed memory item (key=" + key + "). Remaining items in store: " + remaining));
    }

    private CompletionStage<AiToolCallResult> doClear(AiToolCall call, IAiMemoryStore store) {
        if (store instanceof InMemoryAiMemoryStore) {
            ((InMemoryAiMemoryStore) store).clear();
        } else {
            // Fallback for non-InMemoryAiMemoryStore: remove each item by key
            for (AiMemoryItem item : store.getAll(null)) {
                if (item.getKey() != null) {
                    store.remove(item.getKey());
                }
            }
        }

        return CompletableFuture.completedFuture(successSummary(call, "Cleared all memory items. Remaining items in store: 0"));
    }

    private static int countItems(IAiMemoryStore store) {
        if (store instanceof InMemoryAiMemoryStore) {
            return ((InMemoryAiMemoryStore) store).size();
        }
        return store.getAll(null).size();
    }

    private AiToolCallResult successSummary(AiToolCall call, String summary) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput out = new AiToolOutput();
        out.setBody(summary);
        result.setOutput(out);
        return result;
    }
}
