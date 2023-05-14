/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IRecordOutput<T> extends Closeable {
    default void setHeaderMeta(Map<String, Object> attributes) {

    }

    default void setHeaders(List<String> headers) {

    }

    default void setTrailerMeta(Map<String, Object> trailerMeta) {

    }

    long getWriteCount();

    void write(T record);

    default void writeBatch(Collection<? extends T> records) {
        if (records != null) {
            for (T record : records) {
                write(record);
            }
        }
    }

    void flush();
}