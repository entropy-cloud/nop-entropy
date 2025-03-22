package io.nop.ai.core.commons.debug;

import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.beans.ErrorBean;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DebugMessageHelper {
    public static final String MESSAGE_SEPARATOR = "\n#**********************************#\n";

    public static final String PROMPT_BEGIN = "<[prompt]>\n";
    public static final String PROMPT_END = "\n</[prompt]>\n";
    public static final String THINK_BEGIN = "<think>\n";
    public static final String THINK_END = "\n</think>\n";
    public static final String EXCEPTION_BEGIN = "<AI_EXCEPTION>\n";
    public static final String EXCEPTION_END = "\n</AI_EXCEPTION>\n";

    public static final String METADATA_BEGIN = "<AI_META_DATA>\n";
    public static final String METADATA_END = "\n</AI_META_DATA>\n";

    public static void collectDebugText(StringBuilder sb, AiChatResponse message) {
        if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
            sb.append(METADATA_BEGIN);
            sb.append(JsonTool.stringify(message.getMetadata()));
            sb.append(METADATA_END);
        }

        Prompt prompt = message.getPrompt();
        if (prompt != null) {
            sb.append(PROMPT_BEGIN);
            sb.append(prompt.getLastMessage().getContent());
            sb.append(PROMPT_END);
        }

        if (message.isInvalid()) {
            sb.append(EXCEPTION_BEGIN);
            if (message.getInvalidReason() != null) {
                ErrorBean error = message.getInvalidReason();
                sb.append(JsonTool.stringify(error));
            }
            sb.append(EXCEPTION_END);
        }

        String think = message.getThink();
        if (think != null) {
            sb.append(THINK_BEGIN);
            sb.append(think);
            sb.append(THINK_END);
        }

        String content = message.getContent();
        if (content != null)
            sb.append(content);
    }

    public static String buildDebugText(List<AiChatResponse> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiChatResponse message : messages) {
            collectDebugText(sb, message);
            sb.append(DebugMessageHelper.MESSAGE_SEPARATOR);
        }
        return sb.toString();
    }

    public static String getText(List<AiChatResponse> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiChatResponse message : messages) {
            String content = message.getContent();
            if (content != null)
                sb.append(content).append('\n');
        }
        return sb.toString();
    }

    public static void writeDebugFile(File file, List<AiChatResponse> messages) {
        String text = buildDebugText(messages);
        FileHelper.writeText(file, text, null);
    }

    public static List<AiChatResponse> parseDebugFile(File file) {
        return parseDebugText(FileHelper.readText(file, null));
    }

    public static List<AiChatResponse> parseDebugText(String text) {
        List<String> parts = StringHelper.splitBy(text, MESSAGE_SEPARATOR);
        List<AiChatResponse> ret = new ArrayList<>();
        for (String part : parts) {
            if (StringHelper.isBlank(part))
                continue;
            AiChatResponse res = parseMessage(part);
            ret.add(res);
        }
        return ret;
    }

    static AiChatResponse parseMessage(String text) {
        text = StringHelper.replace(text, "\r\n", "\n");

        AiChatResponse res = new AiChatResponse();

        int pos = 0;
        if (text.startsWith(METADATA_BEGIN, pos)) {
            int pos2 = text.indexOf(METADATA_END, pos + METADATA_BEGIN.length());
            if (pos2 > 0) {
                String block = text.substring(pos + METADATA_BEGIN.length(), pos2);
                Map<String, Object> metadata = JsonTool.parseMap(block);
                res.setMetadata(metadata);
                pos = pos2 + METADATA_END.length();
            }
        }
        if (text.startsWith(PROMPT_BEGIN, pos)) {
            pos = text.indexOf(PROMPT_END, pos + PROMPT_BEGIN.length());
            String prompt = text.substring(PROMPT_BEGIN.length(), pos);
            if (prompt.startsWith(PROMPT_BEGIN))
                prompt = prompt.substring(PROMPT_BEGIN.length());
            res.setPrompt(Prompt.userText(prompt));
            pos += PROMPT_END.length();
        }

        if (text.startsWith(EXCEPTION_BEGIN, pos)) {
            int pos2 = text.indexOf(EXCEPTION_END, pos + EXCEPTION_BEGIN.length());
            if (pos2 > 0) {
                String errorInfo = text.substring(pos + EXCEPTION_BEGIN.length(), pos2);
                try {
                    ErrorBean error = (ErrorBean) JsonTool.parseBeanFromText(errorInfo, ErrorBean.class);
                    res.setInvalidReason(error);
                } catch (Exception e) {
                    ErrorBean error = new ErrorBean();
                    error.setDescription(errorInfo);
                    res.setInvalidReason(error);
                }

                res.setInvalid(true);
                pos = pos2 + EXCEPTION_END.length();
            }
        }

        if (text.startsWith(THINK_BEGIN, pos)) {
            int pos2 = text.indexOf(THINK_END, pos + THINK_BEGIN.length());
            if (pos2 > 0) {
                String think = text.substring(pos + THINK_BEGIN.length(), pos2);
                res.setThink(think);
                pos = pos2 + THINK_END.length();
            }
        }

        String content = text.substring(pos);
        res.setContent(content);
        return res;
    }
}
