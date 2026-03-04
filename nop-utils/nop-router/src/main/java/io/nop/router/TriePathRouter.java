/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router;

import io.nop.commons.util.StringHelper;
import io.nop.router.trie.MatchResult;
import io.nop.router.trie.PatternChild;
import io.nop.router.trie.Trie;
import io.nop.router.trie.TrieNode;
import io.nop.router.trie.MatchResult;
import io.nop.router.trie.Trie;
import io.nop.router.trie.TrieNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TriePathRouter<E> extends Trie<List<RouteValue<E>>> {

    public void addMultiPathPattern(String pattern, E value) {
        if (pattern.indexOf('|') < 0) {
            addPathPattern(pattern, value);
        } else {
            List<String> list = StringHelper.split(pattern, '|');
            list.forEach(item -> addPathPattern(item, value));
        }
    }

    public void addMatchAll(E value) {
        List<String> list = List.of("*path");
        makeNode(list, node -> {
            List<String> varNames = List.of("path");
            node.setTillEnd(true);
            addValue(node, varNames, value);
        });
    }

    public void addPathPattern(String pattern, E value) {
        // 支持 ** 语法糖，转换为 {*path}
        if (pattern.endsWith("/**")) {
            pattern = pattern.substring(0, pattern.length() - 3) + "/{*path}";
        }

        List<String> list = parseRoute(pattern);

        if (list.isEmpty()) {
            makeNode(Collections.emptyList(), node -> {
                addValue(node, Collections.emptyList(), value);
            });
        } else {
            String lastSegment = list.get(list.size() - 1);
            PatternInfo patternInfo = parseLastSegmentPattern(lastSegment);

            if (patternInfo != null) {
                // 最后一段是模式匹配（如 *.json 或 page_{var}.json）
                List<String> pathWithoutLast = list.subList(0, list.size() - 1);
                makeNode(pathWithoutLast, node -> {
                    // 创建或获取 pattern child
                    PatternChild<List<RouteValue<E>>> patternChild = node.getPatternChild();
                    if (patternChild == null) {
                        TrieNode<List<RouteValue<E>>> childNode = new TrieNode<>();
                        patternChild = new PatternChild<>(
                            patternInfo.prefix,
                            patternInfo.suffix,
                            patternInfo.varName,
                            childNode
                        );
                        node.setPatternChild(patternChild);
                    }

                    TrieNode<List<RouteValue<E>>> childNode = patternChild.getChild();
                    List<String> varNames = buildVarNames(list, patternInfo.varName);
                    addValue(childNode, varNames, value);
                });
            } else {
                // 普通模式
                makeNode(list, node -> {
                    List<String> varNames = getVarNames(list);
                    String last = varNames.get(varNames.size() - 1);
                    boolean tillEnd = last != null && last.startsWith("*");
                    node.setTillEnd(tillEnd);
                    addValue(node, varNames, value);
                });
            }
        }
    }

    public MatchResult<List<RouteValue<E>>> matchPath(String path) {
        return match(this.parseRoute(path));
    }

    /**
     * 匹配所有可能的路径模式，返回所有匹配结果
     */
    public List<MatchResult<List<RouteValue<E>>>> matchAllPath(String path) {
        return matchAll(this.parseRoute(path));
    }

    /**
     * 匹配所有可能的路径模式，只返回匹配的值集合
     */
    public Set<E> matchAllPathValues(String path) {
        Set<List<RouteValue<E>>> matched = matchAllValues(this.parseRoute(path));
        Set<E> result = new HashSet<>();
        for (List<RouteValue<E>> routeValues : matched) {
            for (RouteValue<E> rv : routeValues) {
                result.add(rv.getValue());
            }
        }
        return result;
    }

    public List<String> parseRoute(String path) {
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        return StringHelper.split(path, '/');
    }

    private List<String> getVarNames(List<String> list) {
        List<String> varNames = new ArrayList<>(list.size());
        for (String name : list) {
            if (name.startsWith("{") && name.endsWith("}")) {
                varNames.add(name.substring(1, name.length() - 1));
            } else {
                varNames.add(null);
            }
        }
        return varNames;
    }

    private void addValue(TrieNode<List<RouteValue<E>>> node, List<String> varNames, E value) {
        List<RouteValue<E>> list = node.getValue();
        if (list == null) {
            list = new ArrayList<>();
            node.setValue(list);
        }
        list.add(new RouteValue<>(varNames, value));
    }

    /**
     * 解析最后一段的模式，如 *.json, page_{var}.json, file_{name}
     *
     * @param segment 路径段
     * @return 如果是模式返回 PatternInfo，否则返回 null
     */
    private PatternInfo parseLastSegmentPattern(String segment) {
        if (segment == null || segment.isEmpty()) {
            return null;
        }

        // 检查是否是 {var} 形式（纯变量）
        if (segment.startsWith("{") && segment.endsWith("}")) {
            return null; // 这是普通通配符，不是模式匹配
        }

        // 检查是否包含 {var} 变量
        int braceStart = segment.indexOf('{');
        int braceEnd = segment.indexOf('}');

        if (braceStart >= 0 && braceEnd > braceStart) {
            // 形如 page_{var}.json 或 file_{var}
            String prefix = segment.substring(0, braceStart);
            String varName = segment.substring(braceStart + 1, braceEnd);
            String suffix = segment.substring(braceEnd + 1);
            return new PatternInfo(prefix, suffix, varName);
        }

        // 检查是否是 *.ext 形式
        if (segment.startsWith("*") && segment.length() > 1) {
            String suffix = segment.substring(1); // 包含 . 如 .json
            return new PatternInfo("", suffix, null);
        }

        return null;
    }

    /**
     * 构建变量名列表，处理最后一段的模式变量
     */
    private List<String> buildVarNames(List<String> segments, String lastVarName) {
        List<String> varNames = new ArrayList<>(segments.size() + 1);
        for (int i = 0; i < segments.size() - 1; i++) {
            String seg = segments.get(i);
            if (seg.startsWith("{") && seg.endsWith("}")) {
                varNames.add(seg.substring(1, seg.length() - 1));
            } else {
                varNames.add(null);
            }
        }
        // 最后一段的模式变量
        varNames.add(lastVarName);
        return varNames;
    }

    /**
     * 模式信息
     */
    private static class PatternInfo {
        final String prefix;
        final String suffix;
        final String varName;

        PatternInfo(String prefix, String suffix, String varName) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.varName = varName;
        }
    }
}
