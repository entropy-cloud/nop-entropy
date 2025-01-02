package io.nop.stream.core.common.eventtime;

/**
 * A {@code TimestampAssigner} assigns event time timestamps to elements. These timestamps are used
 * by all functions that operate on event time, for example event time windows.
 *
 * <p>Timestamps can be an arbitrary {@code long} value, but all built-in implementations represent
 * it as the milliseconds since the Epoch (midnight, January 1, 1970 UTC), the same way as {@link
 * System#currentTimeMillis()} does it.
 *
 * @param <T> The type of the elements to which this assigner assigns timestamps.
 */
@FunctionalInterface
public interface TimestampAssigner<T> {

    /**
     * The value that is passed to {@link #extractTimestamp} when there is no previous timestamp
     * attached to the record.
     */
    long NO_TIMESTAMP = Long.MIN_VALUE;

    /**
     * Assigns a timestamp to an element, in milliseconds since the Epoch. This is independent of
     * any particular time zone or calendar.
     *
     * <p>The method is passed the previously assigned timestamp of the element. That previous
     * timestamp may have been assigned from a previous assigner. If the element did not carry a
     * timestamp before, this value is {@link #NO_TIMESTAMP} (= {@code Long.MIN_VALUE}: {@value
     * Long#MIN_VALUE}).
     *
     * @param element         The element that the timestamp will be assigned to.
     * @param recordTimestamp The current internal timestamp of the element, or a negative value, if
     *                        no timestamp has been assigned yet.
     * @return The new timestamp.
     */
    long extractTimestamp(T element, long recordTimestamp);
}
