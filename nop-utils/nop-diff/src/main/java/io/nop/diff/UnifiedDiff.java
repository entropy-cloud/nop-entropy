/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表示单个文件的 unified diff
 * <p>
 * 完整格式示例:
 * --- a/file.txt
 * +++ b/file.txt
 * @@ -1,2 +1,3 @@
 *  Hello
 * -World
 * +Git
 * +World
 */
@DataBean
public class UnifiedDiff {
    /**
     * 旧文件路径（--- 后面的路径）
     */
    private final String oldPath;

    /**
     * 新文件路径（+++ 后面的路径）
     */
    private final String newPath;

    /**
     * 旧文件时间戳（可选）
     */
    private final String oldTimestamp;

    /**
     * 新文件时间戳（可选）
     */
    private final String newTimestamp;

    /**
     * 扩展头部信息（如 index, new file mode 等）
     */
    private final List<String> extendedHeaders;

    /**
     * 所有 hunks
     */
    private final List<UnifiedDiffHunk> hunks;

    public UnifiedDiff(@JsonProperty("oldPath") String oldPath,
                       @JsonProperty("newPath") String newPath,
                       @JsonProperty("oldTimestamp") String oldTimestamp,
                       @JsonProperty("newTimestamp") String newTimestamp,
                       @JsonProperty("extendedHeaders") List<String> extendedHeaders,
                       @JsonProperty("hunks") List<UnifiedDiffHunk> hunks) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.oldTimestamp = oldTimestamp;
        this.newTimestamp = newTimestamp;
        this.extendedHeaders = extendedHeaders != null
                ? Collections.unmodifiableList(new ArrayList<>(extendedHeaders))
                : Collections.emptyList();
        this.hunks = hunks != null
                ? Collections.unmodifiableList(new ArrayList<>(hunks))
                : Collections.emptyList();
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public String getOldTimestamp() {
        return oldTimestamp;
    }

    public String getNewTimestamp() {
        return newTimestamp;
    }

    public List<String> getExtendedHeaders() {
        return extendedHeaders;
    }

    public List<UnifiedDiffHunk> getHunks() {
        return hunks;
    }

    /**
     * 是否为新文件（add）
     */
    public boolean isNewFile() {
        return "/dev/null".equals(oldPath) || oldPath == null;
    }

    /**
     * 是否为删除文件（delete）
     */
    public boolean isDeletedFile() {
        return "/dev/null".equals(newPath) || newPath == null;
    }

    /**
     * 是否为重命名
     */
    public boolean isRename() {
        if (oldPath == null || newPath == null) return false;
        if ("/dev/null".equals(oldPath) || "/dev/null".equals(newPath)) return false;
        
        String normalizedOld = normalizePath(oldPath);
        String normalizedNew = normalizePath(newPath);
        return !normalizedOld.equals(normalizedNew);
    }

    /**
     * 获取有效文件路径（用于应用 diff）
     */
    public String getTargetPath() {
        if (isDeletedFile()) {
            return normalizePath(oldPath);
        }
        return normalizePath(newPath);
    }

    private String normalizePath(String path) {
        if (path == null || "/dev/null".equals(path)) {
            return null;
        }
        // 移除 a/ 或 b/ 前缀
        if (path.startsWith("a/") || path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    /**
     * 转换为 unified diff 格式的字符串
     */
    public String toDiffString() {
        StringBuilder sb = new StringBuilder();
        toDiffString(sb);
        return sb.toString();
    }

    /**
     * 将 diff 内容写入 StringBuilder
     *
     * @param sb 目标 StringBuilder
     */
    public void toDiffString(StringBuilder sb) {
        // 写入扩展头部
        for (String header : extendedHeaders) {
            sb.append(header).append("\n");
        }

        // 写入 --- 行
        if (oldPath != null) {
            sb.append("--- ").append(oldPath);
            if (oldTimestamp != null) {
                sb.append("\t").append(oldTimestamp);
            }
            sb.append("\n");
        }

        // 写入 +++ 行
        if (newPath != null) {
            sb.append("+++ ").append(newPath);
            if (newTimestamp != null) {
                sb.append("\t").append(newTimestamp);
            }
            sb.append("\n");
        }

        // 写入所有 hunks
        for (UnifiedDiffHunk hunk : hunks) {
            hunk.toDiffString(sb);
        }
    }

    @Override
    public String toString() {
        return toDiffString();
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String oldPath;
        private String newPath;
        private String oldTimestamp;
        private String newTimestamp;
        private final List<String> extendedHeaders = new ArrayList<>();
        private final List<UnifiedDiffHunk> hunks = new ArrayList<>();

        public Builder oldPath(String oldPath) {
            this.oldPath = oldPath;
            return this;
        }

        public Builder newPath(String newPath) {
            this.newPath = newPath;
            return this;
        }

        public Builder oldTimestamp(String oldTimestamp) {
            this.oldTimestamp = oldTimestamp;
            return this;
        }

        public Builder newTimestamp(String newTimestamp) {
            this.newTimestamp = newTimestamp;
            return this;
        }

        public Builder addExtendedHeader(String header) {
            this.extendedHeaders.add(header);
            return this;
        }

        public Builder addHunk(UnifiedDiffHunk hunk) {
            this.hunks.add(hunk);
            return this;
        }

        public UnifiedDiff build() {
            return new UnifiedDiff(oldPath, newPath, oldTimestamp, newTimestamp,
                    extendedHeaders, hunks);
        }
    }
}
