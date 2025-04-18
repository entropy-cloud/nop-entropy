package io.nop.markdown.simple;

import io.nop.commons.mutable.MutableInt;

import java.util.ArrayList;
import java.util.List;

public class MarkdownBlockParser {
    public List<MarkdownBlock> parseBlocks(String text) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        text = text.trim();

        MutableInt index = new MutableInt();

        if (text.startsWith("#")) {
            MarkdownBlock block = parseBlock(text, index);
            blocks.add(block);
        } else if (text.startsWith("\n#")) {
            index.set(1);
        }

        do {
            MarkdownBlock block = parseBlock(text, index);
            if (block == null)
                break;
            blocks.add(block);
        } while (true);

        return blocks;
    }

    MarkdownBlock parseBlock(String text, MutableInt index) {
        int pos = index.get();
        if (text.length() <= pos)
            return null;

        int pos2 = text.indexOf("\n#", pos);
        if (pos2 < 0) {
            pos2 = text.length();
        } else {
            pos2++;
        }
        index.set(pos2);

        MarkdownBlock block = new MarkdownBlock();
        block.setStartIndex(pos);
        block.setEndIndex(pos2);

        int level = countBlockLevel(text, pos);
        block.setLevel(level);

        if (level > 0) {
            int pos3 = text.indexOf("\n", pos);
            if (pos3 < 0)
                pos3 = text.length();

            String title = text.substring(pos + level, pos3).trim();
            block.setTitle(title);
            pos = pos3 + 1;
        }

        if (pos < pos2) {
            block.setText(text.substring(pos, pos2));
        }

        return block;
    }

    int countBlockLevel(String text, int pos) {
        int count = 0;
        for (int i = pos, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (c == '#') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}