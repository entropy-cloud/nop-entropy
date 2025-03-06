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

public class DebugMessageHelper {
    public static final String MESSAGE_SEPARATOR = "#**********************************#";

    public static final String PROMPT_BEGIN = "<[prompt]>\n";
    public static final String PROMPT_END = "\n</[prompt]>\n";
    public static final String THINK_BEGIN = "<think>\n";
    public static final String THINK_END = "\n</think>\n";
    public static final String EXCEPTION_BEGIN = "<AI_EXCEPTION>\n";
    public static final String EXCEPTION_END = "\n</AI_EXCEPTION>\n";

    public static void collectDebugText(StringBuilder sb, AiChatResponse message) {
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
                sb.append(JsonTool.serializeToJson(error));
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
        if (text.startsWith(PROMPT_BEGIN)) {
            pos = text.indexOf(PROMPT_END);
            String prompt = text.substring(0, pos);
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
