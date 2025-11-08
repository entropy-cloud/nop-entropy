package io.nop.ai.core.response;

import io.nop.commons.text.SourceCodeBlock;
import io.nop.commons.util.StringHelper;
import io.nop.markdown.simple.MarkdownCodeBlockParser;

public class CodeResponseParser {
    private static CodeResponseParser s_instance = new CodeResponseParser();

    public static CodeResponseParser instance() {
        return s_instance;
    }

    public static void registerInstance(CodeResponseParser parser) {
        s_instance = parser;
    }

    protected CodeResponseParser() {
    }

    public SourceCodeBlock parseResponse(String content, String lang) {
        content = StringHelper.normalizeCRLF(content, false);
        return new MarkdownCodeBlockParser().parseCodeBlockForLang(null, content, lang);
    }
}