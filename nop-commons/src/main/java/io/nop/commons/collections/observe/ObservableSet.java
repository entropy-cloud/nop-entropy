/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.observe;

import java.util.Set;

public class ObservableSet<E> extends ObservableCollection<E> implements Set<E> {
    public ObservableSet(Set<E> collection, ICollectionObserver observer) {
        super(collection, observer);
    }
}