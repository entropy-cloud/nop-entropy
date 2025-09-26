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

        // 如果启用按行切分，则使用新的逻辑
        if (options.isSplitByLine()) {
            return splitByLine(text, options);
        }

        List<SplitChunk> ret = new ArrayList<>();
        text = StringHelper.replace(text, "\r\n", "\n");
        List<String> parts = splitAndCheckSize(text, maxContentSize);

        int index = 0;
        do {
            index = collectOneChunk(parts, index, maxContentSize, options.getOverlapSize(), ret);
        } while (index > 0);
        return ret;
    }

    /**
     * 按行切分的实现，确保每个块的边界都是整行
     */
    protected List<SplitChunk> splitByLine(String text, SplitOptions options) {
        int maxContentSize = options.getMaxContentSize();
        int overlapSize = Math.min(options.getOverlapSize(), maxContentSize - 1); // 重叠大小不能超过maxContentSize-1

        List<SplitChunk> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            chunks.add(new SplitChunk("text", "", "chunk-1"));
            return chunks;
        }

        text = StringHelper.replace(text, "\r\n", "\n");
        String[] lines = text.split("\n", -1); // 保留空行

        List<String> currentChunkLines = new ArrayList<>();
        int currentChunkSize = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 计算当前行的大小（包括换行符，除了最后一行）
            int lineSize = line.length() + (i < lines.length - 1 ? 1 : 0);

            // 处理超长行
            if (line.length() > maxContentSize) {
                // 先保存当前块（如果有内容）
                if (!currentChunkLines.isEmpty()) {
                    chunks.add(createChunk(currentChunkLines, chunks.size()));
                    currentChunkLines.clear();
                    currentChunkSize = 0;
                }

                // 拆分超长行
                List<SplitChunk> longLineChunks = splitLongLine(line, maxContentSize);
                chunks.addAll(longLineChunks);
                continue;
            }

            // 检查添加当前行是否会超过限制
            boolean willExceed = currentChunkSize + lineSize > maxContentSize;

            if (willExceed && !currentChunkLines.isEmpty()) {
                // 保存当前块
                chunks.add(createChunk(currentChunkLines, chunks.size()));

                // 处理重叠
                if (overlapSize > 0) {
                    currentChunkLines = getOverlapLines(currentChunkLines, overlapSize);
                    currentChunkSize = calculateChunkSize(currentChunkLines);

                    // 检查重叠后是否还能容纳当前行
                    if (currentChunkSize + lineSize > maxContentSize) {
                        // 如果重叠后仍然无法容纳当前行，清空重叠内容
                        currentChunkLines.clear();
                        currentChunkSize = 0;
                    }
                } else {
                    currentChunkLines.clear();
                    currentChunkSize = 0;
                }
            }

            // 如果当前块为空且当前行单独就超过限制（但又不是超长行），需要特殊处理
            if (currentChunkLines.isEmpty() && lineSize > maxContentSize) {
                // 这应该不会发生，因为超长行已经在上面的if块处理了
                // 但为了安全，还是添加保护
                List<SplitChunk> lineChunks = splitLongLine(line, maxContentSize);
                chunks.addAll(lineChunks);
                continue;
            }

            // 添加当前行到块中
            currentChunkLines.add(line);
            currentChunkSize += lineSize;
        }

        // 添加最后一个块
        if (!currentChunkLines.isEmpty()) {
            chunks.add(createChunk(currentChunkLines, chunks.size()));
        }

        return chunks;
    }

    /**
     * 处理超长行的拆分
     */
    protected List<SplitChunk> splitLongLine(String line, int maxContentSize) {
        List<SplitChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        // 确保最大内容大小至少为1
        int effectiveSize = Math.max(1, maxContentSize);

        while (start < line.length()) {
            int end = Math.min(start + effectiveSize, line.length());
            String chunkContent = line.substring(start, end);
            chunks.add(new SplitChunk("text", chunkContent, "chunk-line-" + (chunks.size() + 1)));
            start = end;
        }

        return chunks;
    }

    /**
     * 获取重叠行（修复版）
     */
    protected List<String> getOverlapLines(List<String> previousChunkLines, int overlapSize) {
        if (overlapSize <= 0 || previousChunkLines.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> overlapLines = new ArrayList<>();
        int currentSize = 0;

        // 从后往前选择行
        for (int i = previousChunkLines.size() - 1; i >= 0; i--) {
            String line = previousChunkLines.get(i);
            // 行大小包括换行符（除了第一行）
            int lineSize = line.length() + (overlapLines.isEmpty() ? 0 : 1);

            if (currentSize + lineSize > overlapSize) {
                break;
            }

            overlapLines.add(0, line); // 添加到开头以保持顺序
            currentSize += lineSize;
        }

        return overlapLines;
    }

    /**
     * 计算块的大小（修复版）
     */
    protected int calculateChunkSize(List<String> lines) {
        if (lines.isEmpty()) {
            return 0;
        }

        int size = 0;
        for (int i = 0; i < lines.size(); i++) {
            size += lines.get(i).length();
            if (i < lines.size() - 1) {
                size += 1; // 换行符
            }
        }
        return size;
    }

    /**
     * 创建块（修复版）
     */
    protected SplitChunk createChunk(List<String> lines, int chunkIndex) {
        String content = String.join("\n", lines);
        return new SplitChunk("text", content, "chunk-" + (chunkIndex + 1));
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
