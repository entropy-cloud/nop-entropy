package io.nop.ai.agent.compact;

import java.util.Set;

public class ToolResultTruncator {

    public static final int DEFAULT_TRUNCATION_THRESHOLD_CHARS = 8000;
    public static final int HEAD_CHARS = 6000;
    public static final int TAIL_CHARS = 1000;

    public static final Set<String> NON_TRUNCATABLE_TOOLS = Set.of(
            "ask-oracle", "ask-human"
    );

    public static String truncate(String content, int thresholdChars) {
        return applyBudget(content, thresholdChars, HEAD_CHARS, TAIL_CHARS);
    }

    /**
     * Plan 2297 (WI16): budget-driven unified truncation protocol — head/tail
     * retention with an exact removed-count marker. The retention windows
     * scale with the budget so ANY positive budget is honoured without
     * out-of-bounds access or silent budget inflation (a fixed 6000+1000
     * window under a 2000-char budget would both overflow and blow the
     * budget). head = 75% of the budget, tail = 25%, capped at the default
     * windows; below the 32-char floor there is no retention semantics left
     * to preserve, so the content is hard-cut to the budget.
     */
    public static String applyBudget(String content, int maxChars) {
        int head = Math.min(HEAD_CHARS, maxChars * 3 / 4);
        int tail = Math.min(TAIL_CHARS, maxChars / 4);
        return applyBudget(content, maxChars, head, tail);
    }

    private static String applyBudget(String content, int maxChars, int head, int tail) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        if (maxChars <= 32) {
            return content.substring(0, Math.max(0, maxChars));
        }

        int truncatedCount = content.length() - head - tail;
        String marker = "\n\n... [TRUNCATED: " + truncatedCount + " characters removed] ...\n\n";
        return content.substring(0, head) + marker + content.substring(content.length() - tail);
    }

    public static String truncateIfAllowed(String content, int thresholdChars, String toolName) {
        if (content == null) {
            return content;
        }
        if (toolName != null && NON_TRUNCATABLE_TOOLS.contains(toolName)) {
            return content;
        }
        return truncate(content, thresholdChars);
    }
}
