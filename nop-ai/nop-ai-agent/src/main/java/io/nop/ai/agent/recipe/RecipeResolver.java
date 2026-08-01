package io.nop.ai.agent.recipe;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.AgentNames;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentHookModel;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentRecipeRefModel;
import io.nop.ai.agent.model.RecipeParamModel;
import io.nop.ai.agent.model.recipe.RecipeModel;
import io.nop.ai.core.model.ChatOptionsModel;
import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.ai.core.prompt.node.PromptSyntaxParser;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.KeyedList;
import io.nop.core.resource.component.ResourceComponentManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W6-1 (recipe composition): resolves an agent's declarative {@code <recipes>}
 * references at assembly time, merging the referenced recipes into the
 * {@link AgentModel} before it is consumed by the execution path.
 *
 * <p><b>Adjudication C — merge model</b>: recipes are base layers, the agent's
 * own config is the override layer. Recipes are applied in declared order
 * {@code [R1, R2, ...]} from an empty baseline; the agent's own config is
 * applied last (most specific).
 *
 * <p><b>Adjudication D — prompt composition</b>: merging happens at the
 * <i>source-string layer</i>. Each recipe's {@code prompt-template} (a plain
 * string, parameter-substituted per adjudication G) is concatenated in recipe
 * order; the agent's own prompt source (via {@link IPromptSyntaxNode#getSource()})
 * is appended last. The concatenated string is re-parsed by
 * {@link PromptSyntaxParser} with the same configuration used to load
 * {@code <prompt>} from {@code agent.xml} ({@code enableInclude=true},
 * {@code allowUnknownPrefix=false} — see {@code PromptSyntaxStdDomainHandler}).
 *
 * <p><b>Adjudication E — tools / hooks merge</b>: additive union.
 * {@code merged.tools = agent.tools ∪ ⋃ recipe.tools};
 * {@code merged.hooks = agent.hooks ∪ ⋃ recipe.hooks} (declared order, duplicate
 * hook id fast-fails with the offending id).
 *
 * <p><b>Adjudication F — model-config (chatOptions) merge</b>: field-by-field
 * override, later-wins. A null field on the overlay layer is skipped (the
 * lower layer's value is preserved).
 *
 * <p><b>Adjudication G — template parameterization</b>: {@code {{paramName}}}
 * placeholders in a recipe's {@code prompt-template} are replaced (plain string
 * replace) from the ref's {@code <param>} children. A placeholder with no
 * matching param fast-fails (no silent skip). Substitution is performed on a
 * copy of the template source — the cached {@link RecipeModel} instance is
 * never modified.
 *
 * <p><b>Adjudication I — cache safety</b>: {@link ResourceComponentManager}
 * returns shared cached instances that may be frozen. This resolver
 * {@link AgentModel#cloneInstance() clones} the AgentModel before modifying it.
 * Because {@code cloneInstance()} is a shallow copy, all collection fields are
 * rebuilt as <i>new</i> collection instances (new {@link LinkedHashSet} for
 * tools, new {@link KeyedList} for hooks, new {@link ChatOptionsModel} for
 * chatOptions) before mutation — shared references from the cache are never
 * modified in place. The merged prompt is re-parsed into a fresh AST.
 *
 * <p><b>Fast-path (zero regression)</b>: when the agent has no
 * {@code <recipes>}, the original model instance is returned unchanged
 * ({@code result == original}), skipping all cloning and merging work.
 */
public final class RecipeResolver {

    /**
     * Matches {@code {{paramName}}} placeholders in a recipe prompt-template.
     * The captured group is the param name (trimmed). This is a source-string
     * pattern, not prompt-syntax: recipe templates are plain strings whose
     * only template expressions are these param placeholders.
     */
    private static final Pattern PARAM_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

    private RecipeResolver() {
    }

    /**
     * Resolve and merge the recipes referenced by {@code agent} into a
     * (cloned, mutable) {@link AgentModel}.
     *
     * @param agent the original agent model loaded from VFS (shared cache
     *              instance; never modified by this method)
     * @return the merged agent model, or the exact same instance if the agent
     *         declares no recipes (fast-path zero-regression)
     */
    public static AgentModel resolve(AgentModel agent) {
        if (agent == null) {
            return null;
        }
        List<AgentRecipeRefModel> refs = agent.getRecipes();
        if (refs == null || refs.isEmpty()) {
            // Fast-path: no recipes => return the original instance unchanged.
            return agent;
        }

        // Adjudication I: clone the shared cached instance before any mutation.
        AgentModel merged = agent.cloneInstance();

        // Build merged state from an empty baseline (adjudication C).
        StringBuilder promptBuf = new StringBuilder();
        Set<String> mergedTools = new LinkedHashSet<>();
        KeyedList<AgentHookModel> mergedHooks = new KeyedList<>(AgentHookModel::getId);
        ChatOptionsModel mergedOptions = null;

        // Apply each recipe as a base layer, in declared order.
        for (AgentRecipeRefModel ref : refs) {
            String recipeName = ref.getRef();
            // Adjudication B / fail-loud: ref must be a valid identifier
            // (flows into VFS path concatenation, same guard as agentName).
            if (!AgentNames.isValidIdentifier(recipeName)) {
                throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RECIPE_INVALID_REF)
                        .param(NopAiAgentErrors.ARG_RECIPE_REF, recipeName);
            }
            RecipeModel recipe = loadRecipe(recipeName);

            // Adjudication D/G: render prompt-template (source-string layer).
            String promptTemplate = recipe.getPromptTemplate();
            if (promptTemplate != null && !promptTemplate.isEmpty()) {
                String rendered = renderTemplate(promptTemplate, ref, recipeName);
                promptBuf.append(rendered);
            }

            // Adjudication E: tools union.
            if (recipe.getTools() != null) {
                mergedTools.addAll(recipe.getTools());
            }

            // Adjudication E: hooks union (duplicate id fast-fail).
            if (recipe.getHooks() != null) {
                for (AgentHookModel hook : recipe.getHooks()) {
                    addHook(mergedHooks, hook);
                }
            }

            // Adjudication F: model-config field-by-field override.
            if (recipe.getModelConfig() != null) {
                mergedOptions = mergeChatOptions(mergedOptions, recipe.getModelConfig());
            }
        }

        // Apply the agent's own config as the override layer (applied last,
        // most specific — adjudication C).
        if (agent.getPrompt() != null) {
            promptBuf.append(agent.getPrompt().getSource());
        }
        if (agent.getTools() != null) {
            mergedTools.addAll(agent.getTools());
        }
        if (agent.getHooks() != null) {
            for (AgentHookModel hook : agent.getHooks()) {
                addHook(mergedHooks, hook);
            }
        }
        if (agent.getChatOptions() != null) {
            mergedOptions = mergeChatOptions(mergedOptions, agent.getChatOptions());
        }

        // Apply merged state to the clone (new collection instances —
        // adjudication I cache safety).
        if (promptBuf.length() > 0) {
            SourceLocation loc = agent.getPrompt() != null
                    ? agent.getPrompt().getLocation()
                    : SourceLocation.fromPath("/" + agent.getName() + ".agent.xml");
            IPromptSyntaxNode parsedPrompt = new PromptSyntaxParser()
                    .enableInclude(true)
                    .parseFromText(loc, promptBuf.toString());
            merged.setPrompt(parsedPrompt);
        }
        merged.setTools(mergedTools);
        merged.setHooks(mergedHooks);
        merged.setChatOptions(mergedOptions);
        // Recipes have been applied; clear the pending refs to signal
        // "merged" and prevent accidental double-resolution downstream.
        merged.setRecipes(Collections.emptyList());

        return merged;
    }

    private static RecipeModel loadRecipe(String recipeName) {
        String path = "/" + recipeName + ".recipe.xml";
        try {
            Object obj = ResourceComponentManager.instance().loadComponentModel(path);
            if (!(obj instanceof RecipeModel)) {
                throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RECIPE_NOT_FOUND)
                        .param(NopAiAgentErrors.ARG_RECIPE_REF, recipeName);
            }
            return (RecipeModel) obj;
        } catch (NopAiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RECIPE_NOT_FOUND, e)
                    .param(NopAiAgentErrors.ARG_RECIPE_REF, recipeName);
        }
    }

    /**
     * Adjudication G: replace {@code {{paramName}}} placeholders in the
     * template source with the corresponding {@code <param>} values. A
     * placeholder without a matching param fast-fails (no silent skip). The
     * substitution works on a fresh {@link StringBuilder}; the cached
     * RecipeModel's prompt-template string is never mutated (adjudication I).
     */
    private static String renderTemplate(String template, AgentRecipeRefModel ref, String recipeName) {
        Map<String, String> params = indexParams(ref.getParams(), recipeName);

        Matcher m = PARAM_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String paramName = m.group(1).trim();
            String value = params.get(paramName);
            if (value == null) {
                throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RECIPE_MISSING_PARAM)
                        .param(NopAiAgentErrors.ARG_RECIPE_REF, recipeName)
                        .param(NopAiAgentErrors.ARG_PARAM_NAME, paramName);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, String> indexParams(List<RecipeParamModel> params, String recipeName) {
        Map<String, String> map = new HashMap<>();
        if (params == null || params.isEmpty()) {
            return map;
        }
        for (RecipeParamModel p : params) {
            map.put(p.getName(), p.getValue());
        }
        return map;
    }

    /**
     * Adjudication E: add a hook to the merged registry, fast-failing on a
     * duplicate id (no silent dedupe).
     */
    private static void addHook(KeyedList<AgentHookModel> hooks, AgentHookModel hook) {
        String hookId = hook.getId();
        if (hookId != null && hooks.containsKey(hookId)) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AGENT_RECIPE_DUPLICATE_HOOK_ID)
                    .param(NopAiAgentErrors.ARG_HOOK_ID, hookId);
        }
        hooks.add(hook);
    }

    /**
     * Adjudication F: merge two {@link ChatOptionsModel} layers field-by-field.
     * Non-null fields on the overlay replace the base; null fields on the
     * overlay preserve the base value. Always returns a fresh instance
     * (adjudication I: neither input instance is mutated).
     */
    private static ChatOptionsModel mergeChatOptions(ChatOptionsModel base, ChatOptionsModel overlay) {
        ChatOptionsModel result = new ChatOptionsModel();
        if (base != null) {
            result.setProvider(base.getProvider());
            result.setModel(base.getModel());
            result.setSeed(base.getSeed());
            result.setTemperature(base.getTemperature());
            result.setTopP(base.getTopP());
            result.setTopK(base.getTopK());
            result.setMaxTokens(base.getMaxTokens());
            result.setContextLength(base.getContextLength());
            result.setStop(base.getStop());
        }
        if (overlay.getProvider() != null) {
            result.setProvider(overlay.getProvider());
        }
        if (overlay.getModel() != null) {
            result.setModel(overlay.getModel());
        }
        if (overlay.getSeed() != null) {
            result.setSeed(overlay.getSeed());
        }
        if (overlay.getTemperature() != null) {
            result.setTemperature(overlay.getTemperature());
        }
        if (overlay.getTopP() != null) {
            result.setTopP(overlay.getTopP());
        }
        if (overlay.getTopK() != null) {
            result.setTopK(overlay.getTopK());
        }
        if (overlay.getMaxTokens() != null) {
            result.setMaxTokens(overlay.getMaxTokens());
        }
        if (overlay.getContextLength() != null) {
            result.setContextLength(overlay.getContextLength());
        }
        if (overlay.getStop() != null) {
            result.setStop(overlay.getStop());
        }
        return result;
    }
}
