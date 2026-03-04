/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router.trie;

/**
 * 支持最后一段路径的模式匹配，如:
 * - *.json (prefix="", suffix=".json", varName=null)
 * - page_{index}.json (prefix="page_", suffix=".json", varName="index")
 * - file_{name} (prefix="file_", suffix="", varName="name")
 */
public class PatternChild<V> {
    private final String prefix;
    private final String suffix;
    private final String varName;
    private final TrieNode<V> child;

    public PatternChild(String prefix, String suffix, String varName, TrieNode<V> child) {
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.varName = varName;
        this.child = child;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getVarName() {
        return varName;
    }

    public TrieNode<V> getChild() {
        return child;
    }

    /**
     * 检查给定的路径段是否匹配此模式
     *
     * @param segment 路径段
     * @return 如果匹配返回 true
     */
    public boolean matches(String segment) {
        if (segment == null)
            return false;

        // 检查长度是否足够
        int minLen = prefix.length() + suffix.length();
        if (segment.length() < minLen)
            return false;

        // 检查前缀和后缀
        return segment.startsWith(prefix) && segment.endsWith(suffix);
    }

    /**
     * 从路径段中提取变量值
     *
     * @param segment 路径段
     * @return 变量值，如果没有变量则返回 null
     */
    public String extractValue(String segment) {
        if (!matches(segment) || varName == null)
            return null;

        // 提取中间部分作为变量值
        int start = prefix.length();
        int end = segment.length() - suffix.length();
        if (start >= end)
            return "";
        return segment.substring(start, end);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PatternChild[");
        if (!prefix.isEmpty()) {
            sb.append("prefix='").append(prefix).append("'");
        }
        if (varName != null) {
            if (!prefix.isEmpty())
                sb.append(", ");
            sb.append("var='").append(varName).append("'");
        }
        if (!suffix.isEmpty()) {
            sb.append(", suffix='").append(suffix).append("'");
        }
        sb.append("]");
        return sb.toString();
    }
}
