package io.nop.ai.core.commons.splitter;

import io.nop.commons.util.StringHelper;

import java.util.List;

/**
 * 将文本切分为多个分块，每个分块的大小不超过maxContentSize。切分过程中尽量保持code block和table的完整性，不把它们切分到多个不同的分块中。
 */
public class MarkdownTextSplitter extends SimpleTextSplitter {

    @Override
    protected int collectOneChunk(List<String> parts, int index, int maxContentSize,
                                  List<SplitChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = index, n = parts.size(); i < n; i++) {
            String line = parts.get(i);
            if (isCodeBlockStart(line)) {
                int end = findCodeBlockEnd(parts, i);
                if (end > 0) {
                    // 确定是code block
                    i = tryAddBlock(sb, parts, i, end, maxContentSize, chunks);
                    if (sb.length() >= maxContentSize)
                        break;
                    continue;
                }
            } else if (isTableBlockStart(line)) {
                int end = findTableBlockEnd(parts, i);
                if (end > 0) {
                    // 确定是table block
                    i = tryAddBlock(sb, parts, i, end, maxContentSize, chunks);
                    if (sb.length() >= maxContentSize)
                        break;
                    continue;
                }
            }

            if (sb.length() + line.length() <= maxContentSize) {
                sb.append(line).append('\n');
            } else if (sb.length() > 0) {
                chunks.add(new SplitChunk("text", sb.toString()));
                sb.setLength(0);
                return i;
            } else {
                chunks.add(new SplitChunk("text", line));
                return i;
            }
        }
        if (sb.length() > 0) {
            chunks.add(new SplitChunk("text", sb.toString()));
        }
        return -1;
    }

    boolean isCodeBlockStart(String line) {
        return StringHelper.trimLeft(line).startsWith("```");
    }

    int findCodeBlockEnd(List<String> parts, int index) {
        String first = parts.get(index);
        int blankCount = first.indexOf("```");
        String codeBlockEnd = StringHelper.repeat(" ", blankCount) + "```";
        for (int i = index + 1, n = parts.size(); i < n; i++) {
            if (parts.get(i).startsWith(codeBlockEnd))
                return i + 1;
        }
        return -1;
    }

    boolean isTableBlockStart(String line) {
        if (line.length() < 3)
            return false;

        return line.startsWith("|") && (line.endsWith("|") || line.trim().endsWith("|"));
    }

    int findTableBlockEnd(List<String> parts, int index) {
        for (int i = index + 1, n = parts.size(); i < n; i++) {
            if (!isTableBlockStart(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    int tryAddBlock(StringBuilder sb, List<String> parts, int index, int end, int maxContentSize, List<SplitChunk> chunks) {
        int total = TextSplitHelper.sumLength(parts, index, end);
        if (sb.length() + total <= maxContentSize) {
            TextSplitHelper.append(sb, parts, index, end);
            return end - 1;
        }

        // 当前内容 + block内容超长，则直接返回chunk
        if (sb.length() > 0) {
            chunks.add(new SplitChunk(null, sb.toString()));
            sb.setLength(0);
            // 下次循环重试当前行
            return index - 1;
        }

        // block本身超长
        addBlock(sb, parts, index, end, Math.min(maxContentSize * 2, 4096), chunks);
        return end - 1;
    }

    void addBlock(StringBuilder sb, List<String> parts, int index, int end, int maxContentSize, List<SplitChunk> chunks) {
        for (int i = index; i < end; i++) {
            String line = parts.get(i);
            if (sb.length() <= 0) {
                sb.append(line).append('\n');
                continue;
            }

            if (sb.length() + line.length() <= maxContentSize) {
                sb.append(line).append('\n');
            } else {
                chunks.add(new SplitChunk(null, sb.toString()));
                sb.setLength(0);
                sb.append(line).append('\n');
            }
        }

        if (sb.length() > 0) {
            chunks.add(new SplitChunk(null, sb.toString()));
            sb.setLength(0);
        }
    }
}
