package io.nop.ai.core.response;

import io.nop.commons.util.StringHelper;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;

public class MarkdownResponseParser {
    private static MarkdownResponseParser s_instance = new MarkdownResponseParser();

    protected MarkdownResponseParser() {

    }

    public static void registerInstance(MarkdownResponseParser parser) {
        s_instance = parser;
    }

    public static MarkdownResponseParser instance() {
        return s_instance;
    }

    public MarkdownDocument parseResponse(String text) {
        if (StringHelper.isEmpty(text))
            return null;
        text = text.trim();
        int pos = 0;
        int pos2 = text.length();
        if (text.startsWith("```markdown")) {
            pos = "```markdown".length();
        }else if(text.startsWith("```plaintext")){
            pos = "```plaintext".length();
        }
        if (pos > 0 && text.endsWith("\n```"))
            pos2 = text.length() - "\n```".length();
        text = text.substring(pos, pos2);
        return MarkdownTool.instance().parseFromText(null, text);
    }
}
