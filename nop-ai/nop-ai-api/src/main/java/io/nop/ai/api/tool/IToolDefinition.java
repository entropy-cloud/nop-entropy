package io.nop.ai.api.tool;

import java.util.Collections;
import java.util.Set;

/**
 * Lightweight contract for anything that can be presented to an LLM as a
 * callable tool. Implemented by both the chat-API {@code ChatToolDefinition}
 * (the runtime tool descriptor sent to LLM providers) and the toolkit
 * {@code AiToolModel} (the declarative tool definition loaded from
 * {@code *.tool.xml}).
 *
 * <p>The {@link #getTags()} default returns an empty set so existing
 * implementors are unaffected; tag-aware implementors override it to expose
 * their declared tag set for {@code AgentModel.activeTags} filtering.
 */
public interface IToolDefinition {

    /**
     * @return the tool name (unique identifier presented to the LLM)
     */
    String getName();

    /**
     * @return a human-readable description of what the tool does
     */
    String getDescription();

    /**
     * @return the tool's visibility tags (may be empty). Used by the agent's
     *         {@code activeTags} selector to filter which tools are exposed
     *         to the LLM at runtime. An empty set means "no tags" — the tool
     *         is visible to all activeTags selections (including empty
     *         activeTags = full visibility).
     */
    default Set<String> getTags() {
        return Collections.emptySet();
    }
}
