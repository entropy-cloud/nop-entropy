/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.record;

import io.nop.commons.record.impl.LimitRecordInput;
import io.nop.commons.record.impl.RecordInputImpls;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 统一数据库表和数据文件读取
 *
 * @param <T> 记录对象的类型
 */
public interface IRecordInput<T> extends Closeable, Iterator<T>, Iterable<T> {
    default @Nonnull Iterator<T> iterator() {
        return this;
    }

    default IRecordResourceMeta getMeta() {
        return null;
    }

    default Map<String, Object> getHeaderMeta() {
        IRecordResourceMeta meta = getMeta();
        return meta == null ? null : meta.getHeaderMeta();
    }

    default Map<String, Object> getTrailerMeta() {
        IRecordResourceMeta meta = getMeta();
        return meta == null ? null : meta.getTrailerMeta();
    }

    default long getTotalCount() {
        return -1L;
    }

    long getReadCount();

    default long getRemainingCount() {
        long count = getTotalCount();
        if (count < 0)
            return -1L;
        return count - getReadCount();
    }

    default Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * 跳过count条记录
     *
     * @param count 期望跳过的最大条目数。
     * @return 实际跳过的条目数
     */
    default long skip(long count) {
        return RecordInputImpls.defaultSkip(this, count);
    }

    /**
     * 限制最多读取maxCount条记录。返回一个新的{@link IRecordInput}对象，原对象不受影响
     */
    default IRecordInput<T> limit(long maxCount) {
        return new LimitRecordInput<>(this, maxCount);
    }

    default @Nonnull List<T> readBatch(int maxCount) {
        return RecordInputImpls.defaultReadBatch(this, maxCount);
    }

    default void readBatch(int maxCount, Consumer<T> ret) {
        RecordInputImpls.defaultReadBatch(this, maxCount, ret);
    }

    default @Nonnull List<T> readAll() {
        return RecordInputImpls.defaultReadAll(this);
    }
}