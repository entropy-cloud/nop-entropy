package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;

public class MarkdownCodeBlockParser {
    static final String CODE_BLOCK_START = "```";
    static final String MIDDLE_CODE_BLOCK_START = "\n```";

    public MarkdownCodeBlock parseCodeBlock(SourceLocation loc, String text, int startPos) {
        int quoteStart = findQuoteStart(text, startPos);
        if (quoteStart < 0) {
            return null;
        }

        int quoteEnd = findQuoteEnd(text, quoteStart);
        if (quoteEnd >= text.length())
            return null;

        int quoteCount = quoteEnd - quoteStart;

        int langEnd = text.indexOf('\n', quoteEnd);
        if (langEnd < 0)
            return null;

        String endMark = "\n" + StringHelper.repeat("`", quoteCount);
        int blockEnd = text.indexOf(endMark, langEnd);
        if (blockEnd < 0)
            return null;

        int blockEndPos = text.indexOf('\n', blockEnd);
        if (blockEndPos < 0)
            blockEndPos = text.length();

        String lang = text.substring(quoteEnd, langEnd).trim();
        String source = text.substring(langEnd, blockEndPos);

        MarkdownCodeBlock codeBlock = new MarkdownCodeBlock();
        if (loc != null) {
            codeBlock.setLocation(loc.offset(quoteStart - 1, 0));
        }
        codeBlock.setStartPos(quoteStart);
        codeBlock.setEndPos(blockEndPos);
        codeBlock.setLang(lang);
        codeBlock.setSource(source);

        return null;
    }

    int findQuoteStart(String text, int startPos) {
        int pos = text.indexOf(CODE_BLOCK_START, startPos);
        if (pos < 0)
            return pos;
        if (pos == startPos)
            return pos;
        return text.indexOf(MIDDLE_CODE_BLOCK_START, startPos);
    }

    int findQuoteEnd(String text, int pos) {
        for (int i = pos, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (c != '`')
                return i;
        }
        return text.length();
    }
}
