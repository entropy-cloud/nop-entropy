package io.nop.ai.core.commons.splitter;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class SimpleTextSplitter implements IAiTextSplitter {

    @Override
    public List<SplitChunk> split(SourceLocation loc, String text, SplitOptions options) {
        int maxContentSize = options.getMaxContentSize();

        if (text.length() <= maxContentSize)
            return List.of(new SplitChunk(null, text));

        List<SplitChunk> ret = new ArrayList<>();
        text = StringHelper.replace(text, "\r\n", "\n");
        List<String> parts = splitAndCheckSize(text, maxContentSize);

        int index = 0;
        do {
            index = collectOneChunk(parts, index, maxContentSize, options.getOverlapSize(), ret);
        } while (index > 0);
        return ret;
    }

    protected List<String> splitAndCheckSize(String text, int maxChunkSize) {
        List<String> parts = StringHelper.split(text, '\n');

        // 删除最后一个空行
        if (!parts.isEmpty() && parts.get(parts.size() - 1).isEmpty()) {
            parts.remove(parts.size() - 1);
        }

        List<String> result = new ArrayList<>();
        for (String line : parts) {
            if (line.length() <= maxChunkSize) {
                result.add(line);
            } else {
                // 拆分超过最大长度的行
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(start + maxChunkSize, line.length());
                    result.add(line.substring(start, end));
                    start = end;
                }
            }
        }

        return result;
    }

    protected String buildChunkText(List<String> lines, int index, String text, int overlapSize) {
        if (index > 0 && overlapSize > 0) {
            int n = 0;
            List<String> prevLines = new ArrayList<>();
            for (int i = index - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (n < overlapSize) {
                    n += line.length() + 1;
                    prevLines.add(line);
                } else {
                    break;
                }
            }
            prevLines = CollectionHelper.reverseList(prevLines);
            return StringHelper.join(prevLines, "\n") + "\n" + text;
        }
        return text;
    }

    protected int collectOneChunk(List<String> parts, int index, int maxContentSize, int overlapSize,
                                  List<SplitChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = index, n = parts.size(); i < n; i++) {
            String line = parts.get(i);
            if (sb.length() + line.length() <= maxContentSize) {
                sb.append(line).append('\n');
            } else {
                chunks.add(new SplitChunk("text", buildChunkText(parts, index, sb.toString(), overlapSize), "chunk-" + chunks.size() + 1));
                sb.setLength(0);
                return i;
            }
        }
        if (sb.length() > 0) {
            chunks.add(new SplitChunk("text", buildChunkText(parts, index, sb.toString(), overlapSize), "chunk-" + chunks.size() + 1));
        }
        return -1;
    }
}
