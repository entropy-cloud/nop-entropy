/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 输出文件由 文件头 + 文件体 + 文件尾构成。 文件体内包含多行数据
 */
public interface IRecordOutput<T> extends Closeable, Flushable {

    default void beginWrite(Map<String, Object> headerMeta) {

    }

    default void setHeaders(List<String> headers) {

    }

    default void endWrite(Map<String, Object> trailerMeta) {

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

    void flush() throws IOException;
}