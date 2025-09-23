package io.nop.ai.core.file;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class FileDiff {
    private String oldPath;
    private String newPath;
    private List<DiffSection> diffs;

    public enum DiffType {
        ADD, DELETE, MODIFY
    }

    @DataBean
    public static class DiffSection {
        // 差异类型：新增、删除、修改等
        private DiffType type;

        private List<String> leadingContext;  // 支持多行上下文
        private List<String> changedLines;    // 支持多行变更
        private List<String> trailingContext; // 支持多行上下文

        public DiffType getType() {
            return type;
        }

        public void setType(DiffType type) {
            this.type = type;
        }

        public List<String> getLeadingContext() {
            return leadingContext;
        }

        public void setLeadingContext(List<String> leadingContext) {
            this.leadingContext = leadingContext;
        }

        public List<String> getChangedLines() {
            return changedLines;
        }

        public void setChangedLines(List<String> changedLines) {
            this.changedLines = changedLines;
        }

        public List<String> getTrailingContext() {
            return trailingContext;
        }

        public void setTrailingContext(List<String> trailingContext) {
            this.trailingContext = trailingContext;
        }
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }

    public List<DiffSection> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<DiffSection> diffs) {
        this.diffs = diffs;
    }

    /**
     * 判断是否是新增文件
     */
    public boolean isAddedFile() {
        return oldPath == null || oldPath.isEmpty();
    }

    /**
     * 判断是否是删除文件
     */
    public boolean isDeletedFile() {
        return newPath == null || newPath.isEmpty();
    }

    /**
     * 判断是否是重命名文件
     */
    public boolean isRenamedFile() {
        return oldPath != null && newPath != null && !oldPath.equals(newPath);
    }

    /**
     * 获取变更的行数统计
     */
    public int getChangedLineCount() {
        if (diffs == null) return 0;

        int count = 0;
        for (DiffSection section : diffs) {
            if (section.getChangedLines() != null) {
                count += section.getChangedLines().size();
            }
        }
        return count;
    }
}