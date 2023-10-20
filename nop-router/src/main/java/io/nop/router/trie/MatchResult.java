/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
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
