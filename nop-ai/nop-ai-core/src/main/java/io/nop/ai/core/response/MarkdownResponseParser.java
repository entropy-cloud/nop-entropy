package io.nop.ai.core.response;

import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.simple.MarkdownDocumentParser;

public class MarkdownResponseParser {
    public MarkdownDocument parseResponse(String text) {
        return new MarkdownDocumentParser().parseFromText(null, text);
    }
}
