package io.nop.router.trie;

import java.util.List;

public class MatchResult<V> {
    private final List<String> path;
    private final V value;

    public MatchResult(List<String> path, V value) {
        this.path = path;
        this.value = value;
    }

    public List<String> getPath() {
        return path;
    }

    public V getValue() {
        return value;
    }
}
