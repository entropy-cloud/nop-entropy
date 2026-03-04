/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router.trie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchResult<V> {
    private final List<String> path;
    private final V value;
    /**
     * 模式匹配时提取的变量值，如 page_{index}.json 匹配 page_1.json 时 index=1
     */
    private final Map<String, String> extractedVars;

    public MatchResult(List<String> path, V value) {
        this.path = path;
        this.value = value;
        this.extractedVars = null;
    }

    public MatchResult(List<String> path, V value, Map<String, String> extractedVars) {
        this.path = path;
        this.value = value;
        this.extractedVars = extractedVars;
    }

    public List<String> getPath() {
        return path;
    }

    public V getValue() {
        return value;
    }

    /**
     * 获取模式匹配时提取的变量值
     */
    public Map<String, String> getExtractedVars() {
        return extractedVars != null ? extractedVars : Map.of();
    }

    /**
     * 获取指定变量的提取值
     */
    public String getExtractedVar(String name) {
        return extractedVars != null ? extractedVars.get(name) : null;
    }

    /**
     * 创建带提取变量的 MatchResult
     */
    public static <V> MatchResult<V> withExtractedVars(List<String> path, V value, 
            String varName, String extractedValue) {
        Map<String, String> vars = new HashMap<>();
        vars.put(varName, extractedValue);
        return new MatchResult<>(path, value, vars);
    }

    /**
     * 创建带单个提取变量的 MatchResult（便捷方法）
     */
    public static <V> MatchResult<V> withExtractedVar(List<String> path, V value,
            String varName, String extractedValue) {
        return withExtractedVars(path, value, varName, extractedValue);
    }
}
