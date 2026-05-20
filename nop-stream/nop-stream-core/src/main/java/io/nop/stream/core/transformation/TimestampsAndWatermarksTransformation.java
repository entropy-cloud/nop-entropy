/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.typeinfo.TypeInformation;

import java.util.Collections;
import java.util.List;

/**
 * A transformation that assigns timestamps and generates watermarks for a data stream.
 * This transformation wraps an upstream transformation and applies a {@link WatermarkStrategy}
 * to extract event-time timestamps and produce watermarks.
 *
 * @param <T> the type of elements in this stream
 */
public class TimestampsAndWatermarksTransformation<T> extends PhysicalTransformation<T> {

    private static final long serialVersionUID = 1L;

    private final Transformation<T> input;
    private final WatermarkStrategy<T> watermarkStrategy;

    /**
     * Creates a new timestamps and watermarks transformation.
     *
     * @param name              the name of the transformation
     * @param outputType        the output type information
     * @param parallelism       the parallelism for the transformation
     * @param input             the upstream transformation
     * @param watermarkStrategy the strategy for assigning timestamps and generating watermarks
     */
    public TimestampsAndWatermarksTransformation(String name,
                                                  TypeInformation<T> outputType,
                                                  int parallelism,
                                                  Transformation<T> input,
                                                  WatermarkStrategy<T> watermarkStrategy) {
        super(name, outputType, parallelism);
        this.input = input;
        this.watermarkStrategy = watermarkStrategy;
    }

    /**
     * Returns the upstream input transformation.
     *
     * @return the input transformation
     */
    public Transformation<T> getInput() {
        return input;
    }

    /**
     * Returns the watermark strategy used for timestamp assignment and watermark generation.
     *
     * @return the watermark strategy
     */
    public WatermarkStrategy<T> getWatermarkStrategy() {
        return watermarkStrategy;
    }

    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.singletonList(input);
    }
}
