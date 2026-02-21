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
import io.nop.api.core.util.Guard;

/**
 * 表示 unified diff 中的单行内容
 */
@DataBean
public class UnifiedDiffLine {
    /**
     * 行类型
     */
    private final UnifiedDiffLineType type;

    /**
     * 行内容（不包含类型前缀）
     */
    private final String content;

    public UnifiedDiffLine(@JsonProperty("type") UnifiedDiffLineType type,
                           @JsonProperty("content") String content) {
        this.type = Guard.notNull(type, "type");
        this.content = Guard.notEmpty(content, "content");
    }

    public UnifiedDiffLineType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    /**
     * 是否为上下文行
     */
    public boolean isContext() {
        return type == UnifiedDiffLineType.CONTEXT;
    }

    /**
     * 是否为删除行
     */
    public boolean isDelete() {
        return type == UnifiedDiffLineType.DELETE;
    }

    /**
     * 是否为新增行
     */
    public boolean isAdd() {
        return type == UnifiedDiffLineType.ADD;
    }

    /**
     * 转换为 unified diff 格式的字符串
     */
    public String toDiffString() {
        return type.getPrefix() + content;
    }

    @Override
    public String toString() {
        return toDiffString();
    }

    /**
     * 从 diff 行字符串解析
     *
     * @param line diff 行（包含类型前缀）
     * @return 解析后的 UnifiedDiffLine，如果不为有效 diff 行则返回 null
     */
    public static UnifiedDiffLine fromDiffString(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        char prefix = line.charAt(0);
        UnifiedDiffLineType type = UnifiedDiffLineType.fromPrefix(prefix);
        if (type == null) {
            return null;
        }

        return new UnifiedDiffLine(type, line.substring(1));
    }

    /**
     * 创建上下文行
     */
    public static UnifiedDiffLine context(String content) {
        return new UnifiedDiffLine(UnifiedDiffLineType.CONTEXT, content);
    }

    /**
     * 创建删除行
     */
    public static UnifiedDiffLine delete(String content) {
        return new UnifiedDiffLine(UnifiedDiffLineType.DELETE, content);
    }

    /**
     * 创建新增行
     */
    public static UnifiedDiffLine add(String content) {
        return new UnifiedDiffLine(UnifiedDiffLineType.ADD, content);
    }
}
