/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

/**
 * 表示 unified diff 中一行的类型
 */
public enum UnifiedDiffLineType {
    /**
     * 上下文行（未变更，以空格开头）
     */
    CONTEXT(' '),

    /**
     * 删除的行（以 - 开头）
     */
    DELETE('-'),

    /**
     * 新增的行（以 + 开头）
     */
    ADD('+');

    private final char prefix;

    UnifiedDiffLineType(char prefix) {
        this.prefix = prefix;
    }

    public char getPrefix() {
        return prefix;
    }

    /**
     * 根据行首字符解析类型
     *
     * @param c 行首字符
     * @return 对应的类型，如果不识别返回 null
     */
    public static UnifiedDiffLineType fromPrefix(char c) {
        for (UnifiedDiffLineType type : values()) {
            if (type.prefix == c) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断字符是否为有效的 diff 行首字符
     */
    public static boolean isValidPrefix(char c) {
        return fromPrefix(c) != null;
    }
}
