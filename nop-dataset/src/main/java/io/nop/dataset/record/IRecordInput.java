/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record;

import io.nop.dataset.record.impl.LimitRecordInput;
import io.nop.dataset.record.impl.RecordInputImpls;
import io.nop.dataset.record.impl.TransformRecordInput;
import io.nop.dataset.record.support.BatchChunkIterator;
import jakarta.annotation.Nonnull;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 统一数据库表和数据文件读取
 *
 * @param <T> 记录对象的类型
 */
public interface IRecordInput<T> extends Closeable, Iterator<T>, Iterable<T> {

    default void beforeRead(Map<String, Object> map) {

    }

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

    default <R> IRecordInput<R> map(Function<T, R> transformer, IRecordResourceMeta meta) {
        return new TransformRecordInput<>(this, transformer, meta);
    }

    default <R> IRecordInput<R> map(Function<T, R> transformer) {
        return map(transformer, null);
    }

    default @Nonnull List<T> readBatch(int maxCount) {
        return readBatchWithTransformer(maxCount, Function.identity());
    }

    default @Nonnull List<T> readFiltered(int maxCount, Predicate<T> filter) {
        return readFilteredWithTransformer(maxCount, filter, Function.identity());
    }

    default void readBatch(int maxCount, Consumer<T> ret) {
        readBatchWithTransformer(maxCount, Function.identity(), ret);
    }

    default @Nonnull List<T> readAll() {
        return readAllWithTransformer(Function.identity());
    }

    default @Nonnull <R> List<R> readAllWithTransformer(Function<T, R> transformer) {
        return RecordInputImpls.defaultReadAll(this, transformer);
    }

    default @Nonnull <R> List<R> readBatchWithTransformer(int maxCount, Function<T, R> transformer) {
        return RecordInputImpls.defaultReadBatch(this, maxCount, transformer);
    }

    default @Nonnull <R> List<R> readFilteredWithTransformer(int maxCount, Predicate<T> filter,
                                                             Function<T, R> transformer) {
        return RecordInputImpls.defaultReadBatch(this, maxCount, filter, transformer);
    }

    default <R> void readBatchWithTransformer(int maxCount, Function<T, R> transformer, Consumer<R> ret) {
        RecordInputImpls.defaultReadBatch(this, maxCount, transformer, ret);
    }

    default Iterator<List<T>> chunkIterator(int chunkSize) {
        return new BatchChunkIterator<>(this, chunkSize);
    }
}