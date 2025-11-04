package io.nop.markdown.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.markdown.model.MarkdownCodeBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MarkdownCodeBlockParser {
    static final String CODE_BLOCK_START = "```";
    static final String MIDDLE_CODE_BLOCK_START = "\n```";

    public MarkdownCodeBlock parseCodeBlockForLang(SourceLocation loc, String text, String lang) {
        int pos = 0;
        do {
            MarkdownCodeBlock block = parseNextCodeBlock(loc, text, pos);
            if (block == null)
                return null;
            if (lang == null || lang.equals(block.getLang()))
                return block;
            pos = block.getEndPos();
        } while (pos < text.length());
        return null;
    }

    public List<MarkdownCodeBlock> parseAllCodeBlocks(SourceLocation loc, String text, String lang) {
        List<MarkdownCodeBlock> ret = new ArrayList<>();
        forEachCodeBlock(loc, text, block -> {
            if (lang == null || lang.equals(block.getLang()))
                ret.add(block);
        });
        return ret;
    }

    public void forEachCodeBlock(SourceLocation loc, String text, Consumer<MarkdownCodeBlock> consumer) {
        int pos = 0;
        do {
            MarkdownCodeBlock block = parseNextCodeBlock(loc, text, pos);
            if (block == null)
                return;
            consumer.accept(block);
            pos = block.getEndPos();
        } while (pos < text.length());
    }

    /**
     * 解析Markdown文本中的下一个代码块
     *
     * @param loc   源码位置信息，可为null
     * @param text  要解析的文本
     * @param start 开始解析的位置
     * @return 解析到的代码块，如果没有找到则返回null
     * @throws IllegalArgumentException 如果text为null或start无效
     */
    public MarkdownCodeBlock parseNextCodeBlock(SourceLocation loc, String text, int start) {
        // 参数校验
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        if (start < 0 || start > text.length()) {
            throw new IllegalArgumentException("Invalid start position: " + start);
        }

        // 查找代码块开始标记
        int startMark = findQuoteStart(text, start);
        if (startMark < 0 || startMark >= text.length()) {
            return null;
        }

        // 查找开始标记结束位置
        int markEnd = findQuoteEnd(text, startMark);
        if (markEnd < 0 || markEnd >= text.length()) {
            return null;
        }

        // 计算反引号数量
        int tickCount = markEnd - startMark;
        if (tickCount <= 0) {
            return null;
        }

        // 查找语言说明结束位置
        int langEnd = text.indexOf('\n', markEnd);
        if (langEnd < 0) {
            return null;
        }

        // 构建结束标记
        String endMark = "\n" + StringHelper.repeat("`", tickCount);
        int endPos = text.indexOf(endMark, langEnd);
        if (endPos < 0) {
            return null;
        }

        // 确定代码块结束位置
        int blockEnd = text.indexOf('\n', endPos + endMark.length());
        if (blockEnd < 0) {
            blockEnd = text.length();
        } else {
            blockEnd++; // 包含换行符
        }

        // 提取语言和代码
        String lang = text.substring(markEnd, langEnd).trim();
        String code = text.substring(langEnd + 1, endPos).trim();

        // 创建并返回代码块
        MarkdownCodeBlock block = new MarkdownCodeBlock();
        if (loc != null) {
            block.setLocation(loc.offset(startMark, 0));
        }
        block.setStartPos(startMark);
        block.setEndPos(blockEnd);
        block.setLang(lang);
        block.setSource(code);

        return block;
    }

    int findQuoteStart(String text, int startPos) {
        int pos = text.indexOf(CODE_BLOCK_START, startPos);
        if (pos < 0)
            return pos;
        if (pos == startPos)
            return pos;
        pos = text.indexOf(MIDDLE_CODE_BLOCK_START, startPos);
        if (pos > 0)
            pos++;
        return pos;
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