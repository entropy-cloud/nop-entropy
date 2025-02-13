package io.nop.ai.translate.support;

import io.nop.ai.translate.ITextSplitter;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class SimpleTextSplitter implements ITextSplitter {
    public static final SimpleTextSplitter INSTANCE = new SimpleTextSplitter();

    @Override
    public List<SplitChunk> split(String text, int prologSize, int maxContentSize) {
        if (text.length() <= maxContentSize)
            return List.of(new SplitChunk(null, text));

        List<SplitChunk> ret = new ArrayList<>();
        text = StringHelper.replace(text, "\r\n", "\n");
        List<String> parts = StringHelper.split(text, '\n');

        int index = 0;
        do {
            index = collectOneChunk(parts, index, prologSize, maxContentSize, ret);
        } while (index > 0);
        return ret;
    }

    int collectOneChunk(List<String> parts, int index, int prologSize, int maxContentSize, List<SplitChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = index, n = parts.size(); i < n; i++) {
            String line = parts.get(i);
            if (sb.length() + line.length() <= maxContentSize) {
                if (sb.length() > 0)
                    sb.append('\n');
                sb.append(line);
            } else if (sb.length() > 0) {
                chunks.add(new SplitChunk(getProlog(parts, index, prologSize), sb.toString()));
                sb.setLength(0);
                return i;
            } else {
                // 一行太长，直接分隔。
                String part = getPart(line, maxContentSize);
                parts.set(i, line.substring(part.length() + 1));
                chunks.add(new SplitChunk(getProlog(parts, index, prologSize), part));
                return i;
            }
        }
        if (sb.length() > 0) {
            chunks.add(new SplitChunk(getProlog(parts, index, prologSize), sb.toString()));
        }
        return -1;
    }

    String getPart(String line, int maxContentSize) {
        String part = line.substring(0, maxContentSize);
        int index = part.lastIndexOf('。');
        if (index <= 0) {
            index = part.lastIndexOf('.');
            if (index <= 0)
                index = part.lastIndexOf(',');
            if (index <= 0)
                return part;
        }
        return part.substring(0, index + 1);
    }

    String getProlog(List<String> parts, int index, int prologSize) {
        if (index == 0)
            return null;

        List<String> lines = new ArrayList<>();
        int size = 0;
        for (int i = index - 1; i >= 0; i--) {
            String line = parts.get(i);
            if(line.isEmpty()) {
                lines.add("");
                continue;
            }

            if (size + line.length() <= prologSize) {
                lines.add(line);
                size += line.length() + 1;
                if(size >= prologSize)
                    break;
            } else {
                int diff = size + line.length() - prologSize;
                lines.add(line.substring(line.length() - diff, line.length()));
                break;
            }
        }
        return StringHelper.join(CollectionHelper.reverseList(lines), "\n");
    }
}
