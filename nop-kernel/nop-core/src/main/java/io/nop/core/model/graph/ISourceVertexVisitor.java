/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import java.util.List;
import java.util.function.Consumer;

public interface ISourceVertexVisitor<V> {

    List<V> getSourceVertexes(V vertex);

    default void forEachSource(V vertex, Consumer<? super V> action) {
        List<V> list = getSourceVertexes(vertex);
        if (list != null) {
            for (V source : list) {
                action.accept(source);
            }
        }
    }
}