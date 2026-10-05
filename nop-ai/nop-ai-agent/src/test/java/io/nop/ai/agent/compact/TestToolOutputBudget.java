package io.nop.ai.agent.compact;

import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.ai.toolkit.model.AiToolOutputBudgetModel;
import io.nop.ai.toolkit.api.IToolManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2297 (WI16): tool output budget — resolver precedence matrix, clamping,
 * malformed-argument tolerance, small-budget truncation without out-of-bounds,
 * and the xdef-declared budget read through the generated model.
 */
public class TestToolOutputBudget {

    // ========================================================================
    // Per-call extraction: clamping + tolerance
    // ========================================================================

    @Test
    void perCallOverrideExtractedAndClampedToHardCap() {
        assertEquals(500, ToolOutputBudgetResolver.extractPerCallMaxChars(
                "{\"maxOutputChars\":500,\"command\":\"ls\"}", ToolOutputBudgetResolver.HARD_CAP_CHARS));
        assertEquals(ToolOutputBudgetResolver.HARD_CAP_CHARS, ToolOutputBudgetResolver.extractPerCallMaxChars(
                "{\"maxOutputChars\":99999999}", ToolOutputBudgetResolver.HARD_CAP_CHARS),
                "per-call value above the cap is clamped, not rejected");
        assertEquals(0, ToolOutputBudgetResolver.extractPerCallMaxChars(
                "{\"maxOutputChars\":0}", ToolOutputBudgetResolver.HARD_CAP_CHARS),
                "non-positive override is ignored");
        assertEquals(0, ToolOutputBudgetResolver.extractPerCallMaxChars(
                "{\"maxOutputChars\":\"big\"}", ToolOutputBudgetResolver.HARD_CAP_CHARS),
                "numeric string is not a JSON number — ignored (no sloppy-call reward)");
        assertEquals(0, ToolOutputBudgetResolver.extractPerCallMaxChars(
                "not json at all {{{", ToolOutputBudgetResolver.HARD_CAP_CHARS),
                "malformed arguments text must not throw — the knob is never a failure surface");
        assertEquals(0, ToolOutputBudgetResolver.extractPerCallMaxChars(null, ToolOutputBudgetResolver.HARD_CAP_CHARS));
    }

    // ========================================================================
    // applyBudget: small-budget ranges never overflow, never inflate
    // ========================================================================

    @Test
    void applyBudgetHonoursSmallBudgetsWithoutOverflow() {
        String content = "X".repeat(5000);
        // Fixed 6000+1000 windows under a 2000 budget would substring-overflow
        // (round 1 review M1); scaled windows must honour the budget.
        String result = ToolResultTruncator.applyBudget(content, 2000);
        assertTrue(result.length() < 2000 + 64, "result must stay near the budget (marker overhead aside)");
        assertTrue(result.contains("characters removed"));
        assertTrue(result.startsWith("X"), "head retained");

        // Content below the (maxChars, windows) boundary edge that used to overflow.
        String mid = "Y".repeat(5000);
        String result2 = ToolResultTruncator.applyBudget(mid, 2000);
        assertTrue(result2.length() < 2064);

        // Sub-floor budget: hard cut, no retention semantics.
        String tiny = ToolResultTruncator.applyBudget("Z".repeat(1000), 16);
        assertEquals(16, tiny.length());
        assertFalse(tiny.contains("TRUNCATED"));

        // No truncation needed.
        assertEquals("short", ToolResultTruncator.applyBudget("short", 8000));

        // Default-path equivalence: applyBudget with the default threshold and
        // large content must produce the same shape as the legacy truncate.
        String big = "A".repeat(20000);
        assertEquals(ToolResultTruncator.truncate(big, ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS).length(),
                ToolResultTruncator.applyBudget(big, ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS).length(),
                "default path must remain byte-compatible with the legacy protocol shape");
    }

    // ========================================================================
    // Resolution precedence through the generated xdef model
    // ========================================================================

    @Test
    void resolutionPrecedencePerCallOverDeclaredOverDefault() {
        IToolManager manager = managerWithBudget(budget(3000, true));

        // Per-call clamped to the declared 3000.
        assertEquals(3000, ToolOutputBudgetResolver.resolve("bash", "{\"maxOutputChars\":50000}", manager).getMaxChars());
        // Per-call within the declared value passes through.
        assertEquals(1500, ToolOutputBudgetResolver.resolve("bash", "{\"maxOutputChars\":1500}", manager).getMaxChars());
        // No override → declared default.
        assertEquals(3000, ToolOutputBudgetResolver.resolve("bash", "{}", manager).getMaxChars());
        // No declaration → runtime default.
        assertEquals(ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                ToolOutputBudgetResolver.resolve("plain", "{}", managerWithBudget(null)).getMaxChars());
        // No manager (unassembled wiring) → default, never throws.
        assertEquals(ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                ToolOutputBudgetResolver.resolve("plain", null, null).getMaxChars());
    }

    @Test
    void spillFlagDefaultsTrueAndHonoursDeclaration() {
        assertTrue(ToolOutputBudgetResolver.resolve("bash", "{}", managerWithBudget(budget(8000, true))).isSpillAllowed());
        assertFalse(ToolOutputBudgetResolver.resolve("bash", "{}", managerWithBudget(budget(8000, false))).isSpillAllowed(),
                "spill=false declaration must disable the full-text spill path");
        assertTrue(ToolOutputBudgetResolver.resolve("bash", "{}", managerWithBudget(null)).isSpillAllowed(),
                "no declaration → spill allowed (default)");
    }

    private IToolManager managerWithBudget(AiToolOutputBudgetModel budget) {
        return new IToolManager() {
            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                model.setOutputBudget(budget);
                return model;
            }

            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, io.nop.ai.toolkit.api.IToolExecuteContext context) {
                throw new UnsupportedOperationException("not used in budget resolution");
            }

            @Override
            public java.util.concurrent.CompletableFuture<io.nop.ai.toolkit.model.AiToolCallResult> callTool(
                    String toolName, io.nop.ai.toolkit.model.AiToolCall call, io.nop.ai.toolkit.api.IToolExecuteContext context) {
                throw new UnsupportedOperationException("not used in budget resolution");
            }

            @Override
            public java.util.List<io.nop.ai.toolkit.model.AiToolModel> listTools() {
                return java.util.Collections.emptyList();
            }
        };
    }

    private AiToolOutputBudgetModel budget(int maxChars, boolean spill) {
        AiToolOutputBudgetModel model = new AiToolOutputBudgetModel();
        model.setMaxChars(maxChars);
        model.setSpill(spill);
        return model;
    }
}
