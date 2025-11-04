/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.observe;

import java.util.Map;
import java.util.Map.Entry;

public class ObservableEntry<K, V> implements Map.Entry<K, V> {
    private final Map.Entry<K, V> entry;
    private final ICollectionObserver observer;

    public ObservableEntry(Entry<K, V> entry, ICollectionObserver observer) {
        this.entry = entry;
        this.observer = observer;
    }

    @Override
    public K getKey() {
        return entry.getKey();
    }

    @Override
    public V getValue() {
        return entry.getValue();
    }

    @Override
    public V setValue(V value) {
        observer.beforeModify(this);
        V oldValue = entry.setValue(value);
        observer.afterModify(oldValue != value);
        return oldValue;
    }
}