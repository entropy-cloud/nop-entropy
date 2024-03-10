/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router.trie;

import java.util.List;
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
            return _match(rootNode, path, 0, null);
        } finally {
            lock.readLock().unlock();
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
            if (node.getWildcardChild() != null) {
                if (node.getWildcardChild().isTillEnd()) {
                    candidate = node.getWildcardChild();
                }
                return _match(node.getWildcardChild(), path, index + 1, candidate);
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