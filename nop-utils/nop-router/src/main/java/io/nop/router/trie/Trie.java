/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router.trie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class Trie<V> {
    private final TrieNode<V> rootNode = new TrieNode<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void makeNode(List<String> pattern, Consumer<TrieNode<V>> consumer) {
        lock.writeLock().lock();
        try {
            if (pattern.isEmpty()) {
                consumer.accept(rootNode);
            } else {
                _makeNode(rootNode, pattern, 0, consumer);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void _makeNode(TrieNode<V> node, List<String> pattern, int index, Consumer<TrieNode<V>> consumer) {
        boolean last = index == pattern.size() - 1;
        String name = pattern.get(index);
        boolean wildcard = name == null || name.indexOf('{') >= 0;
        TrieNode<V> child;
        if (wildcard) {
            child = node.getWildcardChild();
            if (child == null) {
                child = new TrieNode<>();
                node.setWildcardChild(child);
            }
        } else {
            child = node.makeExactMatchChild(name);
        }

        if (last) {
            consumer.accept(child);
        } else {
            _makeNode(child, pattern, index + 1, consumer);
        }
    }

    public MatchResult<V> match(List<String> path) {
        lock.readLock().lock();
        try {
            // 根路径（如 "/"）注册在 rootNode 上，空 path 时直接检查 rootNode 的值，
            // 与 makeNode(Collections.emptyList(), ...) 的注册行为保持对称
            if (path.isEmpty())
                return rootNode.getValue() != null ? new MatchResult<>(path, rootNode.getValue()) : null;

            return _match(rootNode, path, 0, null, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 匹配所有可能的路径模式，返回所有匹配结果列表
     *
     * @param path 要匹配的路径段列表
     * @return 所有匹配结果列表，如果没有匹配则返回空列表
     */
    public List<MatchResult<V>> matchAll(List<String> path) {
        lock.readLock().lock();
        try {
            List<MatchResult<V>> results = new ArrayList<>();
            if (path.isEmpty()) {
                if (rootNode.getValue() != null)
                    results.add(new MatchResult<>(path, rootNode.getValue()));
                return results;
            }

            _matchAll(rootNode, path, 0, results);
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 匹配所有可能的路径模式，只返回匹配的值集合
     *
     * @param path 要匹配的路径段列表
     * @return 所有匹配值的集合，如果没有匹配则返回空集合
     */
    public Set<V> matchAllValues(List<String> path) {
        lock.readLock().lock();
        try {
            Set<V> results = new HashSet<>();
            if (path.isEmpty()) {
                if (rootNode.getValue() != null)
                    results.add(rootNode.getValue());
                return results;
            }

            _matchAllValues(rootNode, path, 0, results);
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void _matchAllValues(TrieNode<V> node, List<String> path, int index, Set<V> results) {
        boolean last = path.size() == index + 1;
        String name = path.get(index);

        TrieNode<V> exactChild = node.getExactMatchChild(name);
        if (exactChild != null) {
            if (last) {
                if (exactChild.getValue() != null) {
                    results.add(exactChild.getValue());
                }
            } else if (exactChild.hasChild()) {
                _matchAllValues(exactChild, path, index + 1, results);
            }
        }

        TrieNode<V> wildcardChild = node.getWildcardChild();
        if (wildcardChild != null) {
            if (wildcardChild.getValue() != null) {
                if (last || wildcardChild.isTillEnd()) {
                    results.add(wildcardChild.getValue());
                }
            }

            if (!last && !wildcardChild.isTillEnd()) {
                _matchAllValues(wildcardChild, path, index + 1, results);
            }
        }

        // 最后一段：检查 pattern child
        if (last) {
            PatternChild<V> patternChild = node.getPatternChild();
            if (patternChild != null && patternChild.matches(name)) {
                TrieNode<V> patternNode = patternChild.getChild();
                if (patternNode.getValue() != null) {
                    results.add(patternNode.getValue());
                }
            }
        }
    }

    private void _matchAll(TrieNode<V> node, List<String> path, int index, List<MatchResult<V>> results) {
        boolean last = path.size() == index + 1;
        String name = path.get(index);

        TrieNode<V> exactChild = node.getExactMatchChild(name);
        if (exactChild != null) {
            if (last) {
                if (exactChild.getValue() != null) {
                    results.add(new MatchResult<>(path, exactChild.getValue()));
                }
            } else if (exactChild.hasChild()) {
                _matchAll(exactChild, path, index + 1, results);
            }
        }

        TrieNode<V> wildcardChild = node.getWildcardChild();
        if (wildcardChild != null) {
            if (wildcardChild.getValue() != null) {
                if (last || wildcardChild.isTillEnd()) {
                    results.add(new MatchResult<>(path, wildcardChild.getValue()));
                }
            }

            if (!last && !wildcardChild.isTillEnd()) {
                _matchAll(wildcardChild, path, index + 1, results);
            }
        }

        // 最后一段：检查 pattern child
        if (last) {
            PatternChild<V> patternChild = node.getPatternChild();
            if (patternChild != null && patternChild.matches(name)) {
                TrieNode<V> patternNode = patternChild.getChild();
                if (patternNode.getValue() != null) {
                    // 提取模式变量值
                    String varName = patternChild.getVarName();
                    if (varName != null) {
                        String extractedValue = patternChild.extractValue(name);
                        results.add(MatchResult.withExtractedVar(path, patternNode.getValue(), varName, extractedValue));
                    } else {
                        results.add(new MatchResult<>(path, patternNode.getValue()));
                    }
                }
            }
        }
    }

    private MatchResult<V> _match(TrieNode<V> node, List<String> path, int index,
            TrieNode<V> candidate, PatternMatchInfo<V> patternInfo) {
        boolean last = path.size() == index + 1;

        String name = path.get(index);
        TrieNode<V> child = node.getExactMatchChild(name);
        if (child != null) {
            if (last) {
                if (child.getValue() != null) {
                    // 严格匹配
                    candidate = child;
                    patternInfo = null; // 清除模式信息，因为精确匹配优先
                }
                // 最后一段也要检查 patternChild
                if (candidate == null) {
                    PatternMatchInfo<V> pInfo = matchPatternChild(node, name);
                    if (pInfo != null) {
                        candidate = pInfo.node;
                        patternInfo = pInfo;
                    }
                }
                return makeResult(path, candidate, patternInfo);
            } else {
                if (child.hasChild()) {
                    MatchResult<V> result = _match(child, path, index + 1, candidate, patternInfo);
                    if (result != null)
                        return result;
                }
                // 精确子树没有命中：回退到本层的 tillEnd 通配节点（兜底路由语义）。
                // 例如根上注册了 addMatchAll 的 {*path}，同时存在 "/api/users" 精确前缀，
                // 请求 "/api/orders" 沿 "api" 前缀走到底无匹配时仍应命中兜底
                return matchTillEndFallback(node, path, candidate, patternInfo);
            }
        } else {
            TrieNode<V> wildcardChild = node.getWildcardChild();
            if (wildcardChild != null) {
                if (last) {
                    // 已经是最后一段路径，如果通配符节点有值则作为候选
                    if (wildcardChild.getValue() != null) {
                        candidate = wildcardChild;
                        patternInfo = null;
                    }
                    // 最后一段也要检查 patternChild
                    if (candidate == null) {
                        PatternMatchInfo<V> pInfo = matchPatternChild(node, name);
                        if (pInfo != null) {
                            candidate = pInfo.node;
                            patternInfo = pInfo;
                        }
                    }
                    return makeResult(path, candidate, patternInfo);
                }
                if (wildcardChild.isTillEnd()) {
                    candidate = wildcardChild;
                    return makeResult(path, candidate, patternInfo);
                }
                return _match(wildcardChild, path, index + 1, candidate, patternInfo);
            } else {
                // 最后一段：检查 pattern child
                if (last) {
                    PatternMatchInfo<V> pInfo = matchPatternChild(node, name);
                    if (pInfo != null) {
                        candidate = pInfo.node;
                        patternInfo = pInfo;
                    }
                    return makeResult(path, candidate, patternInfo);
                }
                // 没有任何匹配的子节点，只能返回已经匹配的节点
                return makeResult(path, candidate, patternInfo);
            }
        }
    }

    /**
     * 精确子树未命中时，尝试本节点的 tillEnd 通配子节点作为兜底候选
     */
    private MatchResult<V> matchTillEndFallback(TrieNode<V> node, List<String> path,
            TrieNode<V> candidate, PatternMatchInfo<V> patternInfo) {
        TrieNode<V> wildcardChild = node.getWildcardChild();
        if (wildcardChild != null && wildcardChild.isTillEnd() && wildcardChild.getValue() != null) {
            candidate = wildcardChild;
            patternInfo = null;
        }
        return makeResult(path, candidate, patternInfo);
    }

    /**
     * 模式匹配结果信息
     */
    private static class PatternMatchInfo<V> {
        final TrieNode<V> node;
        final String varName;
        final String extractedValue;

        PatternMatchInfo(TrieNode<V> node, String varName, String extractedValue) {
            this.node = node;
            this.varName = varName;
            this.extractedValue = extractedValue;
        }
    }

    /**
     * 尝试匹配 pattern child
     */
    private PatternMatchInfo<V> matchPatternChild(TrieNode<V> node, String name) {
        PatternChild<V> patternChild = node.getPatternChild();
        if (patternChild != null && patternChild.matches(name)) {
            String varName = patternChild.getVarName();
            String extractedValue = varName != null ? patternChild.extractValue(name) : null;
            return new PatternMatchInfo<>(patternChild.getChild(), varName, extractedValue);
        }
        return null;
    }

    MatchResult<V> makeResult(List<String> path, TrieNode<V> candidate, PatternMatchInfo<V> patternInfo) {
        if (candidate == null)
            return null;
        if (patternInfo != null && patternInfo.varName != null) {
            return MatchResult.withExtractedVar(path, candidate.getValue(),
                patternInfo.varName, patternInfo.extractedValue);
        }
        return new MatchResult<>(path, candidate.getValue());
    }
}
