package io.nop.ai.agent.router;

import io.nop.ai.agent.budget.BudgetSnapshot;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 209 Phase 1 focused tests for {@link SmartModelRouter} (Minimum Rules
 * #23 wiring-relevant unit coverage, #24 no silent skip, #25 new feature
 * coverage).
 */
public class TestSmartModelRouter {

    // ========================================================================
    // Helpers
    // ========================================================================

    private static ChatOptions tier(String provider, String model) {
        ChatOptions o = new ChatOptions();
        o.setProvider(provider);
        o.setModel(model);
        return o;
    }

    private static SmartModelRouter threeTierRouter() {
        return SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("cheap", "cheap-model"))
                .tierModel(Complexity.MEDIUM, tier("mid", "mid-model"))
                .tierModel(Complexity.COMPLEX, tier("strong", "strong-model"))
                .build();
    }

    private static AgentExecutionContext ctxWithBudget(boolean exceeded) {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-budget");
        BigDecimal cost = exceeded ? new BigDecimal("10.00") : new BigDecimal("0.10");
        ctx.setBudgetSnapshot(new BudgetSnapshot(cost, 0L, new BigDecimal("1.00")));
        return ctx;
    }

    private static ChatOptions agentOptionsWithTools(int toolCount) {
        ChatOptions o = new ChatOptions();
        o.setProvider("default");
        o.setModel("default-model");
        java.util.List<ChatToolDefinition> tools = new java.util.ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            tools.add(ChatToolDefinition.of("tool_" + i, "tool " + i));
        }
        o.setTools(tools);
        o.autoToolChoice();
        return o;
    }

    // ========================================================================
    // (a) Three-tier routing by complexity
    // ========================================================================

    @Test
    void simpleRequestRoutesToSimpleTier() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(0);
        List<ChatMessage> msgs = List.of(new ChatUserMessage("hi"));

        RoutingResult result = router.route(msgs, agentOptions, null);

        assertEquals("simple", result.getComplexity());
        assertEquals("cheap", result.getOptions().getProvider());
        assertEquals("cheap-model", result.getOptions().getModel());
        assertNotNull(result.getRoutingReason());
        assertTrue(result.getRoutingReason().contains("complexity=simple"));
    }

    @Test
    void mediumRequestRoutesToMediumTier() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(0);
        // ~300 chars → above default mediumChars (200), below complexChars (2000)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append('x');
        }
        List<ChatMessage> msgs = List.of(new ChatUserMessage(sb.toString()));

        RoutingResult result = router.route(msgs, agentOptions, null);

        assertEquals("medium", result.getComplexity());
        assertEquals("mid", result.getOptions().getProvider());
        assertEquals("mid-model", result.getOptions().getModel());
    }

    @Test
    void complexRequestByLengthRoutesToComplexTier() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            sb.append('x');
        }
        List<ChatMessage> msgs = List.of(new ChatUserMessage(sb.toString()));

        RoutingResult result = router.route(msgs, agentOptions, null);

        assertEquals("complex", result.getComplexity());
        assertEquals("strong", result.getOptions().getProvider());
        assertEquals("strong-model", result.getOptions().getModel());
    }

    @Test
    void complexRequestByToolCountRoutesToComplexTier() {
        SmartModelRouter router = threeTierRouter();
        // 5 tools → complexTools threshold (default 5)
        ChatOptions agentOptions = agentOptionsWithTools(5);

        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        RoutingResult result = router.route(msgs, agentOptions, null);

        assertEquals("complex", result.getComplexity());
        assertEquals("strong-model", result.getOptions().getModel());
    }

    @Test
    void codeContentTriggersComplexTier() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(0);
        List<ChatMessage> msgs = List.of(new ChatUserMessage("fix this:\n```\nint x = 0;\n```"));

        RoutingResult result = router.route(msgs, agentOptions, null);

        assertEquals("complex", result.getComplexity());
        assertEquals("strong-model", result.getOptions().getModel());
    }

    @Test
    void routedOptionsPreserveIncomingTools() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(3);

        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        RoutingResult result = router.route(msgs, agentOptions, null);

        // Tools must survive the model swap (merge keeps incoming tools).
        assertNotNull(result.getOptions().getTools());
        assertEquals(3, result.getOptions().getTools().size());
        assertEquals("auto", result.getOptions().getToolChoice());
    }

    // ========================================================================
    // (b) Budget-aware downgrade
    // ========================================================================

    @Test
    void budgetExceededDowngradesToCheaperTier() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(5); // classifies as complex
        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        // exceeded == true
        RoutingResult exceeded = router.route(msgs, agentOptions, ctxWithBudget(true));

        // Downgraded from complex → medium (highest configured tier below complex)
        assertEquals("complex", exceeded.getComplexity(),
                "complexity field records the original classification");
        assertEquals("mid-model", exceeded.getOptions().getModel(),
                "budget exceeded must downgrade to a cheaper tier's model");
        assertTrue(exceeded.getRoutingReason().contains("budget-exceeded"),
                "routing reason must record the budget downgrade");
        assertTrue(exceeded.getRoutingReason().contains("downgraded"));
    }

    @Test
    void budgetNotExceededDoesNotDowngrade() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(5); // classifies as complex
        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        RoutingResult ok = router.route(msgs, agentOptions, ctxWithBudget(false));

        assertEquals("strong-model", ok.getOptions().getModel(),
                "budget not exceeded must keep the classified tier");
        assertTrue(ok.getRoutingReason().contains("complexity=complex"));
        assertNotEquals(-1, ok.getRoutingReason().indexOf("complexity=complex"));
        // ensure no downgrade marker
        assertTrue(!ok.getRoutingReason().contains("downgraded"));
    }

    @Test
    void budgetExceededOnSimpleTierKeepsSimpleWhenNoCheaperConfigured() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(0); // classifies as simple
        List<ChatMessage> msgs = List.of(new ChatUserMessage("hi"));

        RoutingResult result = router.route(msgs, agentOptions, ctxWithBudget(true));

        // No tier cheaper than simple → stays simple, reason notes no cheaper tier
        assertEquals("cheap-model", result.getOptions().getModel(),
                "simple tier's configured model is cheap-model");
        assertTrue(result.getRoutingReason().contains("no cheaper tier configured"),
                "reason must note budget exceeded but no cheaper model available");
    }

    // ========================================================================
    // (e) No budget snapshot (NoOpBudgetProvider) → no downgrade
    // ========================================================================

    @Test
    void nullBudgetSnapshotDoesNotDowngrade() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(5); // complex
        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-no-snapshot");
        // No setBudgetSnapshot → getBudgetSnapshot() == null
        assertNull(ctx.getBudgetSnapshot());

        RoutingResult result = router.route(msgs, agentOptions, ctx);

        assertEquals("strong-model", result.getOptions().getModel(),
                "null budget snapshot must not trigger a downgrade");
    }

    @Test
    void nullContextDoesNotDowngrade() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions agentOptions = agentOptionsWithTools(5); // complex
        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        RoutingResult result = router.route(msgs, agentOptions, null);

        assertEquals("strong-model", result.getOptions().getModel());
    }

    // ========================================================================
    // (d) routingReason non-null and semantically correct
    // ========================================================================

    @Test
    void routingReasonIsAlwaysNonNullAndDescriptive() {
        SmartModelRouter router = threeTierRouter();
        for (int toolCount : new int[]{0, 3, 5}) {
            ChatOptions agentOptions = agentOptionsWithTools(toolCount);
            RoutingResult result = router.route(
                    List.of(new ChatUserMessage("msg")), agentOptions, null);
            assertNotNull(result.getRoutingReason(),
                    "routingReason must never be null");
            assertTrue(result.getRoutingReason().startsWith("complexity="),
                    "routingReason must start with the complexity tag: " + result.getRoutingReason());
        }
    }

    // ========================================================================
    // No silent skip: unconfigured tier throws (Minimum Rules #24)
    // ========================================================================

    @Test
    void unconfiguredClassifiedTierThrowsNopAiAgentException() {
        // Only simple + medium configured; a complex-classified request has no
        // model for its tier → fail loud (no silent null/empty return).
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("cheap", "cheap-model"))
                .tierModel(Complexity.MEDIUM, tier("mid", "mid-model"))
                .build();
        ChatOptions agentOptions = agentOptionsWithTools(5); // complex
        List<ChatMessage> msgs = List.of(new ChatUserMessage("short"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> router.route(msgs, agentOptions, null));
        assertTrue(ex.getMessage().contains("complex"),
                "error message must identify the unconfigured tier: " + ex.getMessage());
    }

    @Test
    void builderRejectsZeroTiers() {
        assertThrows(NopAiAgentException.class, () -> SmartModelRouter.builder().build());
    }

    @Test
    void builderRejectsInvertedThresholds() {
        assertThrows(NopAiAgentException.class,
                () -> SmartModelRouter.builder()
                        .tierModel(Complexity.SIMPLE, tier("p", "m"))
                        .mediumChars(500)
                        .complexChars(100)
                        .build());
    }

    // ========================================================================
    // PassThroughModelRouter inherits the default getFallback (returns null)
    // ========================================================================

    @Test
    void passThroughRouterInheritsNullFallback() {
        ChatOptions opts = tier("openai", "gpt-4");
        assertNull(PassThroughModelRouter.passThrough().getFallback(opts),
                "PassThroughModelRouter must inherit the default null fallback (no change)");
    }

    @Test
    void smartModelRouterWithoutFallbackChainReturnsNull() {
        SmartModelRouter router = threeTierRouter();
        ChatOptions routed = tier("strong", "strong-model");
        assertNull(router.getFallback(routed),
                "No fallback configured → getFallback must return null (fail-loud later)");
    }
}
