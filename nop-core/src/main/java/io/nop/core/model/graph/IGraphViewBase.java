package io.nop.core.model.graph;

import java.util.Collection;

public interface IGraphViewBase<V, E extends IEdge<V>> extends ITargetVertexView<V> {
    Collection<V> vertexSet();

    Collection<E> edgeSet();
}
