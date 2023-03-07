/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class MultiMapCollector<K, V> implements BiConsumer<K, V> {
    private final Map<K, List<V>> map;

    public MultiMapCollector(Map<K, List<V>> map) {
        this.map = map;
    }

    public MultiMapCollector() {
        this(new HashMap<>());
    }

    public Map<K, List<V>> getResultMap() {
        return map;
    }

    @Override
    public void accept(K k, V v) {
        List<V> list = map.computeIfAbsent(k, key -> new ArrayList<>());
        list.add(v);
    }
}
