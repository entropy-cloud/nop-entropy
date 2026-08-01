package io.nop.ai.agent.recipe;

import io.nop.ai.agent.engine.AgentSessionSupport;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentHookModel;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentRecipeRefModel;
import io.nop.ai.agent.model.RecipeParamModel;
import io.nop.ai.agent.model.recipe.RecipeModel;
import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.commons.collections.KeyedList;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6-1 Phase 2 merge-semantic tests for {@link RecipeResolver}.
 *
 * <p>Each adjudication (C–I) has at least one focused verification case:
 * <ul>
 *   <li>C — overlay order: R1 → R2 → agent</li>
 *   <li>D — prompt source-string concatenation order, re-parsed</li>
 *   <li>E — tools/hooks union, duplicate hook id fast-fail</li>
 *   <li>F — chatOptions field-by-field override (null preserves lower)</li>
 *   <li>G — {{param}} source-string replacement, missing param fast-fail</li>
 *   <li>I — cache safety: cached AgentModel + RecipeModel unmodified</li>
 *   <li>zero regression fast-path: no recipes => same instance returned</li>
 * </ul>
 */
public class TestRecipeResolver {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ---- helpers -------------------------------------------------------

    private AgentModel loadAgent(String path) {
        return (AgentModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    private AgentRecipeRefModel ref(String recipeName, String... paramPairs) {
        AgentRecipeRefModel r = new AgentRecipeRefModel();
        r.setRef(recipeName);
        if (paramPairs.length > 0) {
            KeyedList<RecipeParamModel> params = new KeyedList<>(RecipeParamModel::getName);
            for (int i = 0; i < paramPairs.length; i += 2) {
                RecipeParamModel p = new RecipeParamModel();
                p.setName(paramPairs[i]);
                p.setValue(paramPairs[i + 1]);
                params.add(p);
            }
            r.setParams(params);
        }
        return r;
    }

    private AgentModel agentWithRecipes(AgentRecipeRefModel... refs) {
        AgentModel a = new AgentModel();
        a.setName("test-resolver-agent");
        a.setRecipes(Arrays.asList(refs));
        return a;
    }

    private AgentHookModel hook(String id) {
        AgentHookModel h = new AgentHookModel();
        h.setId(id);
        h.setEvent("pre_call");
        return h;
    }

    // ---- 裁定 C/D/G: prompt composition + param rendering + overlay ----

    @Test
    void promptConcatenatesInRecipeThenAgentOrder() {
        // test-recipe-agent references R1(focus=debugging) + R2(lang=English),
        // and has its own prompt "You are the lead agent.".
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        AgentModel merged = RecipeResolver.resolve(original);

        // Adjudication D: merged prompt source = R1(rendered) + R2(rendered) + agent.
        IPromptSyntaxNode prompt = merged.getPrompt();
        assertNotNull(prompt);
        String source = prompt.getSource();
        // Adjudication G: {{focus}} → debugging, {{lang}} → English
        assertTrue(source.contains("You are a debugging expert."),
                "merged prompt must contain R1 rendered template: " + source);
        assertTrue(source.contains("Always respond in English."),
                "merged prompt must contain R2 rendered template: " + source);
        assertTrue(source.contains("You are the lead agent."),
                "merged prompt must contain agent's own prompt: " + source);
        // Adjudication C: order = R1 then R2 then agent.
        int r1Pos = source.indexOf("debugging expert.");
        int r2Pos = source.indexOf("respond in English.");
        int agentPos = source.indexOf("lead agent.");
        assertTrue(r1Pos < r2Pos, "R1 prompt must precede R2 prompt");
        assertTrue(r2Pos < agentPos, "R2 prompt must precede agent prompt");
    }

    private static void assertNotNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNotNull(o);
    }

    // ---- 裁定 E: tools union -------------------------------------------

    @Test
    void toolsAreMergedAsUnion() {
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        AgentModel merged = RecipeResolver.resolve(original);

        // R1 tools: tool-a, tool-b; R2 tools: tool-c; agent tools: tool-d
        Set<String> tools = merged.getTools();
        assertNotNull(tools);
        assertTrue(tools.contains("tool-a"), "union must contain R1 tool-a");
        assertTrue(tools.contains("tool-b"), "union must contain R1 tool-b");
        assertTrue(tools.contains("tool-c"), "union must contain R2 tool-c");
        assertTrue(tools.contains("tool-d"), "union must contain agent tool-d");
        assertEquals(4, tools.size(), "union of all tools");
    }

    // ---- 裁定 E: hooks union + duplicate id fast-fail ------------------

    @Test
    void hooksAreMergedAsUnion() {
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        AgentModel merged = RecipeResolver.resolve(original);

        // R1 has hook r1-hook; agent has no hooks.
        List<AgentHookModel> hooks = merged.getHooks();
        assertNotNull(hooks);
        assertEquals(1, hooks.size(), "union of R1 hooks + agent hooks");
        assertEquals("r1-hook", hooks.get(0).getId());
    }

    @Test
    void duplicateHookIdAcrossRecipeAndAgentFailsLoud() {
        // Build an agent that references R1 (which has hook "r1-hook") AND
        // declares its own hook with the same id "r1-hook".
        AgentModel agent = agentWithRecipes(ref("test-recipe-r1", "focus", "x"));
        agent.setHooks(Collections.singletonList(hook("r1-hook")));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> RecipeResolver.resolve(agent));
        assertTrue(ex.getMessage().contains("r1-hook"),
                "error message must contain the duplicate hook id");
    }

    @Test
    void duplicateHookIdAcrossTwoRecipesFailsLoud() {
        // R1 has hook r1-hook; build a second recipe fixture reference that
        // also has a hook r1-hook by re-using R1 twice.
        AgentModel agent = agentWithRecipes(
                ref("test-recipe-r1", "focus", "x"),
                ref("test-recipe-r1", "focus", "y"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> RecipeResolver.resolve(agent));
        assertTrue(ex.getMessage().contains("r1-hook"),
                "error message must contain the duplicate hook id across recipes");
    }

    // ---- 裁定 F: chatOptions field-by-field override -------------------

    @Test
    void chatOptionsMergeFieldByFieldLaterWinsNullPreserves() {
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        AgentModel merged = RecipeResolver.resolve(original);

        // R1: provider=deepseek, model=r1-model, temperature=0.2
        // R2: model=r2-model, topP=0.9
        // agent: provider=openai, temperature=0.7
        // Merged: provider=openai (agent wins), model=r2-model (R2 wins, agent null),
        //         temperature=0.7 (agent wins), topP=0.9 (R2, no override)
        assertNotNull(merged.getChatOptions());
        assertEquals("openai", merged.getChatOptions().getProvider(),
                "agent provider overrides R1");
        assertEquals("r2-model", merged.getChatOptions().getModel(),
                "R2 model overrides R1, agent has no model => R2 preserved");
        assertEquals(0.7f, merged.getChatOptions().getTemperature(), 0.0001f,
                "agent temperature overrides R1");
        assertEquals(0.9f, merged.getChatOptions().getTopP(), 0.0001f,
                "R2 topP preserved (no upper-layer override)");
        assertNull(merged.getChatOptions().getSeed(),
                "seed was never set by any layer => null");
    }

    // ---- 裁定 G: missing param fast-fail -------------------------------

    @Test
    void missingParamFailsLoud() {
        // R1 template has {{focus}} but we provide no param for it.
        AgentModel agent = agentWithRecipes(ref("test-recipe-r1"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> RecipeResolver.resolve(agent));
        assertTrue(ex.getMessage().contains("focus"),
                "error must name the missing parameter");
        assertTrue(ex.getMessage().contains("test-recipe-r1"),
                "error must name the recipe ref");
    }

    // ---- 裁定 I: cache safety ------------------------------------------

    @Test
    void resolveReturnsCloneNotOriginal() {
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        AgentModel merged = RecipeResolver.resolve(original);

        assertNotSame(original, merged,
                "merged model must be a clone, not the cached original");
    }

    @Test
    void cachedAgentModelIsNotModifiedAfterResolve() {
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        // Snapshot original state.
        String originalPromptSource = original.getPrompt() != null
                ? original.getPrompt().getSource() : null;
        int originalToolCount = original.getTools() != null ? original.getTools().size() : 0;
        int originalHookCount = original.getHooks() != null ? original.getHooks().size() : 0;
        int originalRecipeCount = original.getRecipes() != null
                ? original.getRecipes().size() : 0;

        RecipeResolver.resolve(original);

        // Adjudication I: cached instance must be unchanged.
        assertEquals(originalPromptSource,
                original.getPrompt() != null ? original.getPrompt().getSource() : null,
                "cached agent prompt must not be modified by resolve");
        assertEquals(originalToolCount,
                original.getTools() != null ? original.getTools().size() : 0,
                "cached agent tools must not be modified");
        assertEquals(originalHookCount,
                original.getHooks() != null ? original.getHooks().size() : 0,
                "cached agent hooks must not be modified");
        assertEquals(originalRecipeCount,
                original.getRecipes() != null ? original.getRecipes().size() : 0,
                "cached agent recipes list must not be modified");
    }

    @Test
    void cachedRecipeModelIsNotModifiedByParamRendering() {
        // Adjudication I/G: param rendering must not mutate the cached
        // RecipeModel's prompt-template string.
        RecipeModel r1 = (RecipeModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-recipe-r1.recipe.xml");
        String templateBefore = r1.getPromptTemplate();

        AgentModel agent = agentWithRecipes(ref("test-recipe-r1", "focus", "debugging"));
        RecipeResolver.resolve(agent);

        assertEquals(templateBefore, r1.getPromptTemplate(),
                "cached recipe prompt-template must still contain {{focus}} after resolve "
                        + "(rendering happens on a copy, not the cached instance)");
        assertTrue(r1.getPromptTemplate().contains("{{focus}}"),
                "cached recipe template still has the unsubstituted placeholder");
    }

    @Test
    void mergedModelRecipesClearedToPreventDoubleResolution() {
        AgentModel original = loadAgent("/test-recipe-agent.agent.xml");
        AgentModel merged = RecipeResolver.resolve(original);

        assertTrue(merged.getRecipes() == null || merged.getRecipes().isEmpty(),
                "merged model should have no pending recipe refs (recipes already applied)");
    }

    // ---- zero regression fast-path -------------------------------------

    @Test
    void noRecipesReturnsSameInstanceFastPath() {
        // An agent with no <recipes> must return the exact same instance
        // (zero-regression fast-path, no cloning).
        AgentModel noRecipes = loadAgent("/test-agent.agent.xml");
        AgentModel result = RecipeResolver.resolve(noRecipes);

        assertSame(noRecipes, result,
                "no recipes => must return the same instance (fast-path)");
    }

    @Test
    void nullAgentReturnsNull() {
        assertNull(RecipeResolver.resolve(null));
    }

    // ---- fail-loud: recipe not found / invalid ref ---------------------

    @Test
    void recipeNotFoundFailsLoud() {
        AgentModel agent = agentWithRecipes(ref("does-not-exist-recipe"));
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> RecipeResolver.resolve(agent));
        assertTrue(ex.getMessage().contains("does-not-exist-recipe"),
                "error must name the missing recipe ref");
    }

    @Test
    void invalidRecipeRefFailsLoud() {
        // ref containing path-traversal characters must be rejected.
        AgentRecipeRefModel bad = new AgentRecipeRefModel();
        bad.setRef("../etc/passwd");
        AgentModel agent = agentWithRecipes(bad);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> RecipeResolver.resolve(agent));
        assertTrue(ex.getMessage().contains("../etc/passwd"),
                "error must name the invalid ref");
    }

    // ---- 接线验证: loadAgentModel returns merged model -----------------

    @Test
    void loadAgentModelReturnsMergedModel() {
        // Wiring verification (Minimum Rule #23): loadAgentModel is the
        // single汇聚 point wired to RecipeResolver.resolve. Verify it
        // returns the MERGED model (recipe prompt present), not the raw
        // cached model.
        AgentSessionSupport support = new AgentSessionSupport(
                null, new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>());

        AgentModel merged = support.loadAgentModel("test-recipe-agent");

        // The merged model must contain recipe-rendered prompt segments.
        String promptSource = merged.getPrompt() != null
                ? merged.getPrompt().getSource() : "";
        assertTrue(promptSource.contains("debugging expert."),
                "loadAgentModel must return merged model with recipe prompt: " + promptSource);
        // Recipes applied => refs cleared on the merged instance.
        assertTrue(merged.getRecipes() == null || merged.getRecipes().isEmpty(),
                "merged model from loadAgentModel should have recipes applied (refs cleared)");
        // Tools union present.
        assertTrue(merged.getTools().contains("tool-a"),
                "loadAgentModel must return merged tools union");
    }

    @Test
    void loadAgentModelNoRecipesReturnsUnchanged() {
        // Zero-regression: an agent without recipes loads normally.
        AgentSessionSupport support = new AgentSessionSupport(
                null, new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.ConcurrentHashMap<>());

        AgentModel model = support.loadAgentModel("test-agent");
        assertEquals("test-agent", model.getName());
        // No recipes => prompt unchanged.
        assertTrue(model.getPrompt().getSource().contains("helpful assistant"));
    }
}
