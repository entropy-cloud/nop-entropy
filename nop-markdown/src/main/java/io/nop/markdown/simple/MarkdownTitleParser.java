package io.nop.markdown.simple;

import io.nop.commons.util.StringHelper;
import io.nop.markdown.MarkdownConstants;

import java.util.Map;

import static io.nop.commons.util.StringHelper.isNumberedPrefix;

public class MarkdownTitleParser {
    public static MarkdownDocumentParser INSTANCE = new MarkdownDocumentParser();

    public MarkdownTitle parseTitle(String line) {
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

        Map<String, String> meta = null;

        if (text.endsWith(MarkdownConstants.META_TITLE_SUFFIX)) {
            int pos = text.lastIndexOf(MarkdownConstants.META_TITLE_PREFIX);
            if (pos > 0) {
                String suffix = text.substring(pos + 1, text.length() - 1);
                text = text.substring(0, pos).trim();
                meta = parseMeta(suffix);
            }
        }

        MarkdownTitle title = new MarkdownTitle();
        title.setLevel(level);
        title.setSectionNo(prefix);
        title.setText(text);
        title.setMeta(meta);
        return title;
    }

    Map<String, String> parseMeta(String suffix) {
        return StringHelper.parseStringMap(suffix, ':', ',');
    }
}
