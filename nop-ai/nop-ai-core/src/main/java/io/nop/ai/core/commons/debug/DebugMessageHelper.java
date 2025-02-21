package io.nop.ai.core.commons.debug;

import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.api.messages.Prompt;

public class DebugMessageHelper {
    public static final String MESSAGE_SEPARATOR = "#**********************************#";

    public static void collectDebugText(StringBuilder sb, AiChatResponse message) {
        Prompt prompt = message.getPrompt();
        if (prompt != null) {
            sb.append("<[prompt]>\n");
            sb.append(prompt.getLastMessage().getContent());
            sb.append("</[prompt]>\n");
        }

        String think = message.getThink();
        if (think != null) {
            sb.append("<think>\n");
            sb.append(think);
            sb.append("\n</think>\n");
        }

        String content = message.getContent();
        if (content != null)
            sb.append(content);
    }
}
