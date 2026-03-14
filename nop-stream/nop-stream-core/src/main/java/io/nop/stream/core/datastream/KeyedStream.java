/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;

/**
 * A {@code KeyedStream} represents a {@link DataStream} on which operator state is partitioned by
 * key using a provided {@link KeySelector}.
 *
 * <p>Operations on the {@code KeyedStream} allow for fine-grained access to per-key state.
 * This enables operations like {@link #window(WindowAssigner)} which allows for per-key
 * windowing operations.
 *
 * @param <T> The type of the elements in the KeyedStream.
 * @param <KEY> The type of the key.
 */
public interface KeyedStream<T, KEY> extends DataStream<T> {

    /**
     * Gets the key selector that was used to partition the stream by key.
     *
     * @return The key selector
     */
    KeySelector<T, KEY> getKeySelector();

    /**
     * Windows this {@code KeyedStream} into tumbling event-time windows.
     *
     * <p>Windows create new buckets (windows) for each key and allow performing
     * computations on the elements in each window.
     *
     * @param size The size of the windows in milliseconds.
     * @return The windowed data stream.
     */
    WindowedStream<T, KEY, TimeWindow> timeWindow(long size);

    /**
     * Windows this {@code KeyedStream} into sliding event-time windows.
     *
     * <p>Windows create new buckets (windows) for each key and allow performing
     * computations on the elements in each window.
     *
     * @param size The size of the windows in milliseconds.
     * @param slide The slide interval (the frequency at which new windows are created) in milliseconds.
     * @return The windowed data stream.
     */
    WindowedStream<T, KEY, TimeWindow> timeWindow(long size, long slide);

    /**
     * Windows this {@code KeyedStream} into tumbling count windows.
     *
     * <p>Count windows create new buckets (windows) when the number of elements
     * reaches the specified size.
     *
     * @param size The number of elements per window.
     * @return The windowed data stream.
     */
    WindowedStream<T, KEY, Window> countWindow(long size);

    /**
     * Windows this {@code KeyedStream} into sliding count windows.
     *
     * <p>Count windows create new buckets (windows) when the number of elements
     * reaches the specified size.
     *
     * @param size The number of elements per window.
     * @param slide The slide interval (number of elements).
     * @return The windowed data stream.
     */
    WindowedStream<T, KEY, Window> countWindow(long size, long slide);

    /**
     * Windows this data stream to a {@code WindowedStream}, which allows for
     * windowing operations.
     *
     * <p>Windows can be defined using a {@link WindowAssigner} that specifies how
     * elements are grouped into windows.
     *
     * @param assigner The {@code WindowAssigner} that assigns elements to windows.
     * @return The windowed data stream.
     */
    <W extends Window> WindowedStream<T, KEY, W> window(WindowAssigner<? super T, W> assigner);
}
