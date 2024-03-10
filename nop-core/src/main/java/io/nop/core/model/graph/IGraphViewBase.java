/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.graph;

import java.util.Collection;

public interface IGraphViewBase<V, E extends IEdge<V>> extends ITargetVertexView<V> {
    Collection<V> vertexSet();

    Collection<E> edgeSet();
}
