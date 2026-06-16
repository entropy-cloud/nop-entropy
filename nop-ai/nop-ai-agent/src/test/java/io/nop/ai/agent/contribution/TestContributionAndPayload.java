package io.nop.ai.agent.contribution;

import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContributionAndPayload {

    @Test
    void contributionRejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(null, "id", "src", 0, "payload"));
    }

    @Test
    void contributionRejectsNullOrEmptyId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(ContributionType.PROMPT, null, "src", 0, "p"));
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(ContributionType.PROMPT, "", "src", 0, "p"));
    }

    @Test
    void contributionRejectsNullOrEmptySource() {
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(ContributionType.PROMPT, "id", null, 0, "p"));
        assertThrows(IllegalArgumentException.class,
                () -> new Contribution(ContributionType.PROMPT, "id", "", 0, "p"));
    }

    @Test
    void contributionAllowsNullPayload() {
        // Payload may be null (consumer-validated at resolution time, not here).
        Contribution c = assertDoesNotThrow(
                () -> new Contribution(ContributionType.PROMPT, "id", "src", 0, null));
        assertEquals(null, c.getPayload());
    }

    @Test
    void forHookFactoryWrapsHookPayload() {
        IAgentLifecycleHook hook = ctx -> HookResult.PassResult.instance();
        Contribution c = Contribution.forHook("h1", "plugin", 0, AgentLifecyclePoint.PRE_CALL, hook);
        assertEquals(ContributionType.HOOK, c.getType());
        assertTrue(c.getPayload() instanceof HookPayload);
        HookPayload hp = (HookPayload) c.getPayload();
        assertEquals(AgentLifecyclePoint.PRE_CALL, hp.getPoint());
        assertEquals(hook, hp.getHook());
    }

    @Test
    void forPromptFactoryPayloadIsString() {
        Contribution c = Contribution.forPrompt("p1", "plugin", 0, "instruction text");
        assertEquals(ContributionType.PROMPT, c.getType());
        assertEquals("instruction text", c.getPayload());
    }

    @Test
    void hookPayloadRejectsNullPoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new HookPayload(null, ctx -> HookResult.PassResult.instance()));
    }

    @Test
    void hookPayloadRejectsNullHook() {
        assertThrows(IllegalArgumentException.class,
                () -> new HookPayload(AgentLifecyclePoint.PRE_CALL, null));
    }

    @Test
    void contributionTypeHasExactlySevenValues() {
        // The 7 contribution types from analysis §2.8 — no more, no less.
        assertEquals(7, ContributionType.values().length);
        // Sanity-check each is present.
        for (String name : new String[]{"TOOL", "COMMAND", "HOOK", "MCP_SERVER",
                "PERMISSION_RULE", "PROMPT", "ROUTER"}) {
            assertDoesNotThrow(() -> ContributionType.valueOf(name));
        }
    }
}
