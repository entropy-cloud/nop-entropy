package io.nop.ai.agent.recipe;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.AgentRecipeRefModel;
import io.nop.ai.agent.model.recipe.RecipeModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6-1 Phase 1 model-layer tests: verify {@code recipe.xdef} codegen
 * produced a parseable {@link RecipeModel}, that {@code agent.xdef}'s new
 * {@code <recipes>} sub-model parses into {@link AgentRecipeRefModel} refs
 * with params, and that illegal configuration fails loud (no silent skip).
 */
public class TestRecipeModelLoading {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void validRecipeParsesIntoRecipeModel() {
        RecipeModel r1 = (RecipeModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-recipe-r1.recipe.xml");

        assertNotNull(r1, "RecipeModel should not be null after loading");
        assertEquals("test-recipe-r1", r1.getName(), "recipe name should match");
        assertEquals("1", r1.getVersion(), "recipe version should match");
        assertEquals("You are a {{focus}} expert.", r1.getPromptTemplate(),
                "prompt-template is a plain string (not prompt-syntax)");
        assertNotNull(r1.getTools(), "tools set should not be null");
        assertTrue(r1.getTools().contains("tool-a"), "tools should contain tool-a");
        assertTrue(r1.getTools().contains("tool-b"), "tools should contain tool-b");
        assertNotNull(r1.getModelConfig(), "model-config should not be null");
        assertEquals("deepseek", r1.getModelConfig().getProvider(), "model-config provider");
        assertEquals("r1-model", r1.getModelConfig().getModel(), "model-config model");
        assertEquals(0.2f, r1.getModelConfig().getTemperature(), 0.0001f, "model-config temperature");
        assertNotNull(r1.getHooks(), "hooks list should not be null");
        assertEquals(1, r1.getHooks().size(), "one hook declared");
        assertEquals("r1-hook", r1.getHooks().get(0).getId(), "hook id");
    }

    @Test
    void agentWithRecipesParsesIntoRefList() {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-recipe-agent.agent.xml");

        assertNotNull(model.getRecipes(), "recipes list should not be null");
        assertEquals(2, model.getRecipes().size(), "two recipe refs declared");

        AgentRecipeRefModel ref0 = model.getRecipes().get(0);
        assertEquals("test-recipe-r1", ref0.getRef(), "first ref points to r1");
        assertNotNull(ref0.getParams(), "params list should not be null");
        assertEquals(1, ref0.getParams().size(), "one param for r1");
        assertEquals("focus", ref0.getParams().get(0).getName(), "param name");
        assertEquals("debugging", ref0.getParams().get(0).getValue(), "param value");

        AgentRecipeRefModel ref1 = model.getRecipes().get(1);
        assertEquals("test-recipe-r2", ref1.getRef(), "second ref points to r2");
        assertEquals("lang", ref1.getParams().get(0).getName(), "param name for r2");
        assertEquals("English", ref1.getParams().get(0).getValue(), "param value for r2");
    }

    @Test
    void agentWithoutRecipesIsBackwardCompatible() {
        // Existing agents with no <recipes> must still parse (zero regression).
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-agent.agent.xml");

        assertNotNull(model.getRecipes(), "recipes list should be non-null empty list");
        assertTrue(model.getRecipes().isEmpty(), "no recipes => empty list");
    }

    @Test
    void recipeMissingNameFailsLoud() {
        // name="!string" is mandatory in recipe.xdef; missing it must fail loud
        // at parse time (xdef validation), not return a half-parsed model.
        assertThrows(Exception.class, () ->
                ResourceComponentManager.instance().loadComponentModel("/test-recipe-no-name.recipe.xml"));
    }

    @Test
    void agentRecipeRefMissingRefFailsLoud() {
        // ref="!string" is mandatory; missing it must fail loud at parse time.
        assertThrows(Exception.class, () ->
                ResourceComponentManager.instance().loadComponentModel("/test-recipe-agent-no-ref.agent.xml"));
    }
}
