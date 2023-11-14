/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.graph;

import java.util.Collection;
import java.util.function.Consumer;

public interface ITargetVertexVisitor<V> {

    Collection<V> getTargetVertexes(V vertex);

    default boolean hasOutwardEdge(V vertex) {
        return !getTargetVertexes(vertex).isEmpty();
    }

    default void forEachTarget(V source, Consumer<? super V> action) {
        Collection<V> list = getTargetVertexes(source);
        if (list != null) {
            for (V target : list) {
                action.accept(target);
            }
        }
    }

}