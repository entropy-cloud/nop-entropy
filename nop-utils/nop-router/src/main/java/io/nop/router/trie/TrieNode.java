/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router.trie;

import java.util.HashMap;
import java.util.Map;

public class TrieNode<V> {
    /**
     * 如果不为null，则表示匹配了完整路径
     */
    private V value;


    /**
     * 此配置仅在value不为null时有效。它表示匹配所有后续路径
     */
    private boolean tillEnd;

    private final Map<String, TrieNode<V>> children = new HashMap<>();

    private TrieNode<V> wildcardChild;

    public boolean hasChild() {
        return wildcardChild != null || !children.isEmpty();
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public boolean isTillEnd() {
        return tillEnd;
    }

    public void setTillEnd(boolean tillEnd) {
        this.tillEnd = tillEnd;
    }

    public TrieNode<V> getExactMatchChild(String name) {
        return children.get(name);
    }

    public TrieNode<V> makeExactMatchChild(String name) {
        TrieNode<V> child = children.get(name);
        if (child == null) {
            child = new TrieNode<>();
            children.put(name, child);
        }
        return child;
    }

    public Map<String, TrieNode<V>> getChildren() {
        return children;
    }

    public TrieNode<V> getWildcardChild() {
        return wildcardChild;
    }

    public void setWildcardChild(TrieNode<V> wildcardChild) {
        this.wildcardChild = wildcardChild;
    }
}
