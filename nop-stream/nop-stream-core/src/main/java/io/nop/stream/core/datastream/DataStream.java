/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.common.typeinfo.TypeInformation;

public interface DataStream<T> {
    TypeInformation<T> getType();

    /**
     * It creates a new {@link KeyedStream} that uses the provided key for partitioning its operator
     * states.
     *
     * @param key The KeySelector to be used for extracting the key for partitioning
     * @return The {@link DataStream} with partitioned state (i.e. KeyedStream)
     */
    <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key);


    /**
     * Method for passing user defined operators along with the type information that will transform
     * the DataStream.
     *
     * @param operatorName name of the operator, for logging purposes
     * @param outTypeInfo  the output type of the operator
     * @param operator     the object containing the transformation logic
     * @param <R>          type of the return stream
     * @return the data stream constructed
     */
    <R> SingleOutputStreamOperator<R> transform(
            String operatorName,
            TypeInformation<R> outTypeInfo,
            OneInputStreamOperator<T, R> operator);
}
