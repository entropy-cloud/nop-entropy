/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.common.typeinfo.TypeInformation;


import java.util.Collections;
import java.util.List;

/**
 * A transformation that partitions a data stream based on a key selector and partitioner.
 * This is a logical transformation that represents key-based partitioning operations
 * like keyBy, rebalance, or rescale in a streaming pipeline. Unlike physical transformations,
 * partitioning transformations are primarily structural nodes in the DAG that define
 * how data should be distributed across parallel instances.
 *
 * <p>
 * PartitionTransformation takes an input stream and applies a partitioner to determine
 * which partition each element should be sent to. This is fundamental for stateful
 * operations and for ensuring proper data locality in distributed streaming systems.
 * </p>
 *
 * <p>
 * The partitioner can be configured using different strategies such as hash partitioning,
 * round-robin, or custom partitioning logic. The output of a partitioning transformation
 * maintains the same data type as the input, but partitions the data across multiple
 * parallel instances.
 * </p>
 *
 * @param <T> The type of the elements in the stream
 *
 * @since 1.0.0
 */
public class PartitionTransformation<T> extends Transformation<T> {

    private static final long serialVersionUID = 1L;

    private final Transformation<T> input;
    private final IPartitioner<? super T> partitioner;

    /**
     * Creates a new partition transformation with the specified parameters.
     *
     * @param input the input transformation that provides the data stream
     * @param name the name of the partition transformation
     * @param partitioner the partitioner to use for distributing elements across partitions
     * @param outputType the output type information (same as input type)
     * @param parallelism the parallelism for the partition transformation
     */
    public PartitionTransformation(Transformation<T> input, String name,
                                   IPartitioner<? super T> partitioner,
                                   TypeInformation<T> outputType, int parallelism) {
        super(name, outputType, parallelism);
        this.input = input;
        this.partitioner = partitioner;
    }

    /**
     * Returns the input transformation that provides the data stream to be partitioned.
     *
     * @return the input transformation
     */
    public Transformation<T> getInput() {
        return input;
    }

    /**
     * Returns the partitioner that determines how elements are distributed across partitions.
     * The partitioner takes a key and the total number of partitions, returning an
     * index indicating which partition the element should be sent to.
     *
     * @return the partitioner for this transformation
     */
    public IPartitioner<? super T> getPartitioner() {
        return partitioner;
    }

    /**
     * Returns the input transformations that this transformation depends on.
     * Since partitioning transformations have exactly one input, this returns a
     * singleton list containing the input transformation.
     *
     * @return a singleton list containing the input transformation
     */
    @Override
    public List<Transformation<?>> getInputs() {
        return Collections.singletonList(input);
    }
}