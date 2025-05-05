package io.nop.ai.core.response;

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
        return MarkdownTool.instance().parseFromText(null, text);
    }
}
