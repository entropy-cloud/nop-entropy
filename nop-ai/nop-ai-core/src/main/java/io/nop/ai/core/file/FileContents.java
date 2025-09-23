package io.nop.ai.core.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ITextSerializable;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.markdown.simple.MarkdownSection;

import java.util.ArrayList;
import java.util.List;

@DataBean
public class FileContents implements ITextSerializable {
    private List<FileContent> files;

    public static FileContents empty() {
        return new FileContents();
    }

    public static FileContents fromText(String text) {
        XNode node = XNodeParser.instance().parseFromText(null, text);
        return fromNode(node);
    }

    public static FileContents fromNode(XNode node) {
        FileContents contents = new FileContents();
        if (node.getTagName().equals(FileContent.TAG_FILE)) {
            contents.addFile(FileContent.fromNode(node));
            return contents;
        }
        for (XNode child : node.getChildren()) {
            contents.addFile(FileContent.fromNode(child));
        }
        return contents;
    }

    public int getTotalLength() {
        int total = 0;
        if (getFiles() == null || getFiles().isEmpty())
            return total;

        for (FileContent content : this.getFiles()) {
            total += content.getLength();
        }
        return total;
    }

    /**
     * 根据索引范围获取文件 [start, end)
     */
    public FileContents subFiles(int start, int end) {
        if (files == null || files.isEmpty()) {
            return FileContents.empty();
        }

        int size = files.size();
        start = Math.max(0, start);
        end = Math.min(size, end);

        if (start == 0 && end == size)
            return this;

        if (start >= end) {
            return FileContents.empty();
        }

        FileContents result = new FileContents();
        for (int i = start; i < end; i++) {
            result.addFile(files.get(i));
        }

        return result;
    }

    public FileContents limitTotalLength(int maxTotalLength) {
        if (maxTotalLength <= 0 || maxTotalLength == Integer.MAX_VALUE)
            return this;

        if (size() <= 0)
            return this;

        int total = getTotalLength();
        if (total <= maxTotalLength)
            return this;

        // 计算缩减比例
        double ratio = (double) maxTotalLength / total;

        FileContents result = new FileContents();
        if (files != null && !files.isEmpty()) {
            int remainingLength = maxTotalLength;

            for (int i = 0; i < files.size(); i++) {
                FileContent content = files.get(i);

                if (content.getLength() <= 0) {
                    result.addFile(content);
                    continue;
                }

                int currentLength = content.getLength();
                int targetLength;

                if (i == files.size() - 1) {
                    // 最后一个文件，分配剩余的所有长度
                    targetLength = remainingLength;
                } else {
                    // 按比例计算目标长度
                    targetLength = Math.max(1, (int) Math.round(currentLength * ratio));
                    targetLength = Math.min(targetLength, remainingLength);
                }

                // 使用withLimit方法创建截取后的文件内容
                FileContent limitedContent = content.withLimit(targetLength);
                result.addFile(limitedContent);

                remainingLength -= limitedContent.getLength();
                if (remainingLength <= 0) {
                    break;
                }
            }
        }

        return result;
    }


    @Override
    public String serializeToString() {
        return toNode().serializeToString();
    }

    public List<FileContent> getFiles() {
        return files;
    }

    public void setFiles(List<FileContent> files) {
        this.files = files;
    }

    public boolean isEmpty() {
        return files == null || files.isEmpty();
    }

    public int size() {
        return files == null ? 0 : files.size();
    }

    public void addFile(FileContent file) {
        if (files == null)
            files = new ArrayList<>();
        files.add(file);
    }

    public XNode toNode() {
        XNode node = XNode.make("files");
        if (files == null || files.isEmpty())
            return node;

        for (FileContent content : files) {
            node.appendChild(content.toNode());
        }
        return node;
    }

    public MarkdownSection toMarkdown() {
        MarkdownSection section = new MarkdownSection();
        section.setLevel(1);
        section.setTitle("File Contents");

        if (files != null && !files.isEmpty()) {
            List<MarkdownSection> children = new ArrayList<>(files.size());
            for (FileContent content : files) {
                children.add(content.toMarkdown());
            }
            section.setChildren(children);
        }

        return section;
    }

    public FileContent getFile(String path) {
        if (files == null)
            return null;

        for (FileContent content : files) {
            if (path.equals(content.getPath())) {
                return content;
            }
        }

        return null;
    }

    public boolean containsFile(String path) {
        return getFile(path) != null;
    }

    @JsonIgnore
    public List<String> getFilePaths() {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> paths = new ArrayList<>(files.size());
        for (FileContent content : files) {
            paths.add(content.getPath());
        }
        return paths;
    }

    /**
     * 移除空内容的文件
     */
    public FileContents removeEmptyFiles() {
        if (files == null || files.isEmpty()) {
            return this;
        }

        FileContents result = new FileContents();
        for (FileContent content : files) {
            if (content.getLength() > 0) {
                result.addFile(content);
            }
        }
        return result;
    }

    /**
     * 获取文件数量统计信息
     */
    public String getStatistics() {
        if (files == null || files.isEmpty()) {
            return "No files";
        }

        int totalFiles = files.size();
        int totalLength = getTotalLength();
        int filesWithMoreData = 0;

        for (FileContent content : files) {
            if (content.isHasMoreData()) {
                filesWithMoreData++;
            }
        }

        return String.format("Files: %d, Total length: %d, Truncated files: %d",
                totalFiles, totalLength, filesWithMoreData);
    }
}
