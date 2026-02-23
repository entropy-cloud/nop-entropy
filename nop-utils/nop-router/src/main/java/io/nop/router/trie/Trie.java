/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router.trie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
        if(path.isEmpty())
            return null;

        lock.readLock().lock();
        try {
            return _match(rootNode, path, 0, null);
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
        if (path.isEmpty())
            return Collections.emptyList();

        lock.readLock().lock();
        try {
            List<MatchResult<V>> results = new ArrayList<>();
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
        if (path.isEmpty())
            return Collections.emptySet();

        lock.readLock().lock();
        try {
            Set<V> results = new HashSet<>();
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
    }

    private MatchResult<V> _match(TrieNode<V> node, List<String> path, int index, TrieNode<V> candidate) {
        boolean last = path.size() == index + 1;

        String name = path.get(index);
        TrieNode<V> child = node.getExactMatchChild(name);
        if (child != null) {
            if (last) {
                if (child.getValue() != null) {
                    // 严格匹配
                    candidate = child;
                }
                return makeResult(path, candidate);
            } else {
                if (child.hasChild()) {
                    return _match(child, path, index + 1, candidate);
                }

                // 如果path只匹配了一部分，但是已经没有子模式能够匹配，则检查是否已经存在可匹配模式
                return makeResult(path, candidate);
            }
        } else {
            TrieNode<V> wildcardChild = node.getWildcardChild();
            if (wildcardChild != null) {
                if (last) {
                    // 已经是最后一段路径，如果通配符节点有值则作为候选
                    if (wildcardChild.getValue() != null) {
                        candidate = wildcardChild;
                    }
                    return makeResult(path, candidate);
                }
                if (wildcardChild.isTillEnd()) {
                    candidate = wildcardChild;
                    return makeResult(path, candidate);
                }
                return _match(wildcardChild, path, index + 1, candidate);
            } else {
                // 没有任何匹配的子节点，只能返回已经匹配的节点
                return makeResult(path, candidate);
            }
        }
    }

    MatchResult<V> makeResult(List<String> path, TrieNode<V> candidate) {
        if (candidate == null)
            return null;
        return new MatchResult<>(path, candidate.getValue());
    }
}