package io.nop.ai.agent.compact;

import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.ai.toolkit.model.AiToolOutputBudgetModel;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Plan 2297 (WI16): resolves the model-tunable output budget for one tool
 * call — the schema-built-in counterpart of unreal's per-call
 * {@code max_output_length} (default 40000 / cap 1000000).
 *
 * <p><b>Precedence</b>: per-call {@code maxOutputChars} argument (the model's
 * knob; clamped to the tool's declared {@code <output-budget max-chars>},
 * then to the {@link #HARD_CAP_CHARS} global cap) → per-tool declared
 * {@code max-chars} → {@link ToolResultTruncator#DEFAULT_TRUNCATION_THRESHOLD_CHARS}.
 * Spill allowance comes from the tool's declared {@code spill} flag
 * (default true). {@link ToolResultTruncator#NON_TRUNCATABLE_TOOLS} keeps the
 * highest precedence at the consumer side (never truncated, never spilled).
 *
 * <p><b>Failure posture</b>: the budget knob must never become a failure
 * surface for tool execution — malformed arguments text (model args are
 * frequently mildly malformed), non-numeric values, or non-positive values
 * are ignored with a debug log and resolution falls through to the next
 * precedence level. Schema-level mistakes fail loud at xdef parse time.
 *
 * <p><b>Prefix-cache ruling</b> (plan 2297): no shipped tool's schema is
 * modified — the knob is honoured at runtime whether or not a tool advertises
 * it; advertising is opt-in per tool.
 */
public class ToolOutputBudgetResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ToolOutputBudgetResolver.class);

    /**
     * Reserved per-call argument: the model's output budget override
     * (characters). Clamped, never trusted as-is.
     */
    public static final String PER_CALL_ARG = "maxOutputChars";

    /**
     * Global hard cap (unreal alignment: 1000000).
     */
    public static final long HARD_CAP_CHARS = 1_000_000L;

    /**
     * Resolved budget for one tool call.
     */
    public static final class Budget {
        private final int maxChars;
        private final boolean spillAllowed;

        Budget(int maxChars, boolean spillAllowed) {
            this.maxChars = maxChars;
            this.spillAllowed = spillAllowed;
        }

        public int getMaxChars() {
            return maxChars;
        }

        public boolean isSpillAllowed() {
            return spillAllowed;
        }
    }

    private ToolOutputBudgetResolver() {
    }

    /**
     * Resolve the output budget for a tool call.
     *
     * @param toolName      the tool being dispatched; may be null (defaults)
     * @param argumentsText the raw JSON arguments text from the model; may be
     *                      null/malformed (ignored with debug log)
     * @param toolManager   the tool manager for xdef declaration lookup; may
     *                      be null (unassembled wiring → defaults)
     * @return the resolved budget; never null
     */
    public static Budget resolve(String toolName, String argumentsText, IToolManager toolManager) {
        AiToolOutputBudgetModel declared = loadDeclared(toolName, toolManager);
        int declaredMax = declared != null && declared.getMaxChars() != null && declared.getMaxChars() > 0
                ? declared.getMaxChars()
                : ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS;
        boolean spillAllowed = declared == null || declared.isSpill();

        long cap = declared != null && declared.getMaxChars() != null && declared.getMaxChars() > 0
                ? Math.min(declared.getMaxChars(), HARD_CAP_CHARS)
                : HARD_CAP_CHARS;

        int perCall = extractPerCallMaxChars(argumentsText, cap);
        int maxChars = perCall > 0 ? perCall : declaredMax;
        return new Budget(maxChars, spillAllowed);
    }

    private static AiToolOutputBudgetModel loadDeclared(String toolName, IToolManager toolManager) {
        if (toolName == null || toolManager == null) {
            return null;
        }
        try {
            AiToolModel model = toolManager.loadTool(toolName);
            return model != null ? model.getOutputBudget() : null;
        } catch (Exception e) {
            // Budget lookup must never break tool dispatch.
            LOG.debug("ToolOutputBudgetResolver: failed to load tool model for '{}' — using defaults", toolName, e);
            return null;
        }
    }

    /**
     * Extract the per-call {@code maxOutputChars} override, clamped to
     * {@code [1, cap]}. Any malformation (non-JSON text, non-numeric value,
     * non-positive value) yields 0 = "no override", logged at debug.
     */
    static int extractPerCallMaxChars(String argumentsText, long cap) {
        if (argumentsText == null || argumentsText.isEmpty()) {
            return 0;
        }
        Object parsed;
        try {
            parsed = JsonTool.parseNonStrict(argumentsText);
        } catch (Exception e) {
            LOG.debug("ToolOutputBudgetResolver: arguments text is not JSON — per-call budget override ignored");
            return 0;
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            return 0;
        }
        Object raw = map.get(PER_CALL_ARG);
        if (!(raw instanceof Number number)) {
            // Absent or non-numeric (includes numeric strings — the model must
            // send a real JSON number; tolerating strings would reward sloppy
            // calls). Silently treated as "no override".
            return 0;
        }
        long value = number.longValue();
        if (value <= 0) {
            LOG.debug("ToolOutputBudgetResolver: per-call {}={} is non-positive — ignored", PER_CALL_ARG, value);
            return 0;
        }
        if (value > cap) {
            LOG.debug("ToolOutputBudgetResolver: per-call {}={} clamped to cap {}", PER_CALL_ARG, value, cap);
            value = cap;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

}
