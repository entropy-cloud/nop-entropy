/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.typeinfo.TypeInformation;

/**
 * A DataStreamSource represents a starting point of a DataStream topology.
 * It is created by the {@link io.nop.stream.core.environment.StreamExecutionEnvironment}
 * to read data from sources like collections, files, or external systems.
 * <p>
 * This is a simplified version based on Apache Flink's DataStreamSource.
 *
 * @param <T> The type of the elements produced by this source
 */
public interface DataStreamSource<T> extends SingleOutputStreamOperator<T> {

    /**
     * Returns the type information for the elements of this data stream.
     *
     * @return The type information
     */
    @Override
    TypeInformation<T> getType();
}
