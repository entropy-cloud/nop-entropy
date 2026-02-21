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
 * 表示一个完整的 unified diff 文件，可能包含多个文件的变更
 * <p>
 * 完整格式示例:
 * diff --git a/file1.txt b/file1.txt
 * index 1234567..abcdef 100644
 * --- a/file1.txt
 * +++ b/file1.txt
 * @@ -1,2 +1,3 @@
 *  Hello
 * -World
 * +Git
 * +World
 * diff --git a/file2.txt b/file2.txt
 * ...
 */
@DataBean
public class UnifiedDiffFile {
    /**
     * git diff 头部的源文件路径（可选，git diff 格式）
     */
    private final String gitDiffSource;

    /**
     * 所有文件的 diff
     */
    private final List<UnifiedDiff> diffs;

    public UnifiedDiffFile(@JsonProperty("gitDiffSource") String gitDiffSource,
                           @JsonProperty("diffs") List<UnifiedDiff> diffs) {
        this.gitDiffSource = gitDiffSource;
        this.diffs = diffs != null
                ? Collections.unmodifiableList(new ArrayList<>(diffs))
                : Collections.emptyList();
    }

    public String getGitDiffSource() {
        return gitDiffSource;
    }

    public List<UnifiedDiff> getDiffs() {
        return diffs;
    }

    /**
     * 获取文件数量
     */
    public int getFileCount() {
        return diffs.size();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return diffs.isEmpty();
    }

    /**
     * 转换为 unified diff 格式的字符串
     */
    public String toDiffString() {
        StringBuilder sb = new StringBuilder();

        if (gitDiffSource != null) {
            sb.append("diff --git ").append(gitDiffSource).append("\n");
        }

        for (int i = 0; i < diffs.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(diffs.get(i).toDiffString());
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toDiffString();
    }

    /**
     * 创建仅包含单个文件 diff 的 UnifiedDiffFile
     */
    public static UnifiedDiffFile of(UnifiedDiff diff) {
        return new UnifiedDiffFile(null, Collections.singletonList(diff));
    }

    /**
     * 创建包含多个文件 diff 的 UnifiedDiffFile
     */
    public static UnifiedDiffFile of(List<UnifiedDiff> diffs) {
        return new UnifiedDiffFile(null, diffs);
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String gitDiffSource;
        private final List<UnifiedDiff> diffs = new ArrayList<>();

        public Builder gitDiffSource(String gitDiffSource) {
            this.gitDiffSource = gitDiffSource;
            return this;
        }

        public Builder addDiff(UnifiedDiff diff) {
            this.diffs.add(diff);
            return this;
        }

        public UnifiedDiffFile build() {
            return new UnifiedDiffFile(gitDiffSource, diffs);
        }
    }
}
