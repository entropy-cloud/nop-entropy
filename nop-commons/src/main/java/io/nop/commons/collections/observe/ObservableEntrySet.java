/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.observe;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

public class ObservableEntrySet<K, V> extends ObservableSet<Entry<K, V>> {
    public ObservableEntrySet(Set<Entry<K, V>> collection, ICollectionObserver observer) {
        super(collection, observer);
    }

    static class ObservableEntryIterator<K, V> extends ObservableIterator<Entry<K, V>> {
        public ObservableEntryIterator(Iterator<Entry<K, V>> iterator, ICollectionObserver observer) {
            super(iterator, observer);
        }

        protected Entry<K, V> onReturn(Entry<K, V> entry) {
            return new ObservableEntry<>(entry, getObserver());
        }
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return new ObservableEntryIterator<>(getCollection().iterator(), getObserver());
    }
}
