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
        if (content == null || content.length() <= thresholdChars) {
            return content;
        }

        int truncatedCount = content.length() - HEAD_CHARS - TAIL_CHARS;
        String head = content.substring(0, HEAD_CHARS);
        String tail = content.substring(content.length() - TAIL_CHARS);
        String marker = "\n\n... [TRUNCATED: " + truncatedCount + " characters removed] ...\n\n";

        return head + marker + tail;
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
