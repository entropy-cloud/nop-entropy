/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.convert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public interface ICollectionView extends Iterable<Object> {
    default <T> List<T> toList() {
        Collection<T> c = toCollection();
        if (c == null)
            return null;
        if (c instanceof List)
            return (List<T>) c;
        return new ArrayList<>(c);
    }

    <T> Collection<T> toCollection();

    default <T> Set<T> toSet() {
        Collection<T> c = toCollection();
        if (c == null)
            return null;
        if (c instanceof Set)
            return (Set<T>) c;
        return new LinkedHashSet<>(c);
    }

    default Stream stream() {
        return toCollection().stream();
    }

    default Stream parallelStream() {
        return toCollection().parallelStream();
    }
}