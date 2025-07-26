package io.nop.markdown.simple;

import io.nop.markdown.utils.MarkdownHelper;

import static io.nop.commons.util.StringHelper.isNumberedPrefix;

public class MarkdownSectionHeaderParser {
    public static MarkdownSectionHeaderParser INSTANCE = new MarkdownSectionHeaderParser();

    public MarkdownSectionHeader parseSectionHeader(String line) {
        line = line.trim();
        if (line.isEmpty()) {
            return null; // 不是标题行
        }

        // 1. 解析标题层级（#的数量）
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }

        // 2. 跳过#和空格
        int cursor = level;
        while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
            cursor++;
        }

        // 3. 提取编号和文本
        String remaining = line.substring(cursor);
        remaining = MarkdownHelper.removeStyle(remaining);

        String linkUrl = null;
        if (containsLink(remaining)) {
            int pos = remaining.indexOf("](");
            linkUrl = remaining.substring(pos + 2, remaining.length() - 1).trim();
            remaining = remaining.substring(1, pos).trim();
        }

        String prefix = "";
        String text = remaining;

        // 判断是否包含数字编号（如 "2.1 需求概述"）
        int firstSpace = remaining.indexOf(' ');
        if (firstSpace > 0) {
            String potentialPrefix = remaining.substring(0, firstSpace);
            if (potentialPrefix.endsWith("."))
                potentialPrefix = potentialPrefix.substring(0, potentialPrefix.length() - 1);

            if (isNumberedPrefix(potentialPrefix)) {
                prefix = potentialPrefix;
                text = remaining.substring(firstSpace + 1).trim();
            }
        }

        if (linkUrl == null && containsLink(text)) {
            int pos = text.indexOf("](");
            linkUrl = text.substring(pos + 2, text.length() - 1).trim();
            text = text.substring(0, pos).trim();
        }

        MarkdownSectionHeader title = new MarkdownSectionHeader();
        title.setLevel(level);
        title.setSectionNo(prefix);
        title.setTitle(text.trim());
        title.setLinkUrl(linkUrl);
        return title;
    }

    boolean containsLink(String title) {
        return title.startsWith("[") && title.contains("](") && title.endsWith(")");
    }
}
