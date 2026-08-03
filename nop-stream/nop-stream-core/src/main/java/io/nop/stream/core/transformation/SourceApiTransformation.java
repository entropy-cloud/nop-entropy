/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.source.Source;

/**
 * A transformation that represents a FLIP-27 style split-based source in the streaming DAG.
 *
 * <p>Stage 49 D5: this is a separate transformation class from the legacy
 * {@link SourceTransformation} (which carries a {@code SourceFunction}) so that the
 * SourceFunction path and the new {@link Source} path do not pollute each other's
 * compile-time / runtime contracts. {@code StreamGraphGenerator} dispatches to a
 * {@code SourceReaderOperatorFactory} on this type.
 *
 * <p>The transformation carries the {@link Source} descriptor (serializable). Live
 * enumerator / reader instances are created by the runtime from this descriptor — they are
 * not embedded (Stage 49 D3: splits delivered post-deploy).
 *
 * @param <OUT> the element type produced by this source
 *
 * @see Source
 * @see SourceTransformation
 */
public class SourceApiTransformation<OUT> extends Transformation<OUT> {

    private static final long serialVersionUID = 1L;

    private final Source<OUT, ? extends io.nop.stream.core.source.SourceSplit, ?> source;

    /**
     * Creates a new source-api transformation with the specified parameters.
     *
     * @param name        the name of the source transformation
     * @param source      the FLIP-27 style source descriptor
     * @param outputType  the output type information for the produced elements
     * @param parallelism the parallelism for the source transformation
     */
    public SourceApiTransformation(String name,
                                   Source<OUT, ? extends io.nop.stream.core.source.SourceSplit, ?> source,
                                   TypeInformation<OUT> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.source = source;
    }

    /**
     * Returns the FLIP-27 style source descriptor.
     */
    public Source<OUT, ? extends io.nop.stream.core.source.SourceSplit, ?> getSource() {
        return source;
    }

    /**
     * Sources are leaf nodes in the DAG: no input dependencies.
     */
    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.emptyList();
    }
}
