/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.typeinfo.TypeInformation;

/**
 * A transformation that merges multiple input streams of the same element type into one
 * output stream (Flink {@code DataStream.union} semantics). The union vertex is a real
 * execution vertex: a pass-through {@code StreamUnionOperator} reads every incoming
 * channel of its input gate and forwards each element unchanged, so the merge happens at
 * the vertex's multi-channel input rather than inside a downstream operator.
 *
 * <p>WI6: this is the first multi-input transformation of the graph model —
 * {@link #getInputs()} returns every declared upstream instead of a single one, and
 * {@code StreamGraphGenerator} creates one {@code StreamEdge} per input.
 *
 * @param <T> the element type shared by all inputs and the output
 */
public class UnionTransformation<T> extends PhysicalTransformation<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<Transformation<T>> inputs;

    /**
     * Creates a union transformation over the given inputs. The first input's output
     * type is the union output type (all inputs must share the element type — enforced
     * by {@code DataStream.union} before this constructor runs).
     *
     * @param name        the name of the transformation
     * @param inputs      the upstream transformations to merge, at least one
     * @param parallelism the parallelism for the union vertex
     */
    public UnionTransformation(String name, List<Transformation<T>> inputs, int parallelism) {
        super(name, inputs.get(0).getOutputType(), parallelism);
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
    }

    /**
     * Returns every upstream transformation merged by this union.
     *
     * @return the unmodifiable list of input transformations
     */
    public List<Transformation<T>> getUnionInputs() {
        return inputs;
    }

    @Override
    public List<Transformation<?>> getInputs() {
        return new ArrayList<>(inputs);
    }
}
