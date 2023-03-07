/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import java.util.Objects;

public class Guard {

    public static void checkArgument(boolean b) {
        checkArgument(b, "");
    }

    public static void checkArgument(boolean b, String message) {
        if (!b) {
            throw new IllegalArgumentException("Invalid:" + message);
        }
    }

    public static void checkArgument(boolean b, String message, Object value) {
        if (!b) {
            throw new IllegalArgumentException("Invalid:" + message + ",value=" + value);
        }
    }

    public static void checkArgument(boolean b, String message, Object value, Object value2) {
        if (!b) {
            throw new IllegalArgumentException("Invalid:" + message + ",value=" + value + ",value2=" + value);
        }
    }


    public static void checkState(boolean b) {
        checkState(b, "");
    }

    public static void checkState(boolean b, String message) {
        if (!b) {
            throw new IllegalStateException("Invalid:" + message);
        }
    }

    public static void checkState(boolean b, String message, Object value) {
        if (!b) {
            throw new IllegalStateException("Invalid:" + message + ",value=" + value);
        }
    }

    public static void checkState(boolean b, String message, Object value, Object value2) {
        if (!b) {
            throw new IllegalStateException("Invalid:" + message + ",value=" + value + ",value2=" + value);
        }
    }

    public static int checkPositionIndex(int index, int size) {
        return checkPositionIndex(index, size, "");
    }

    public static int checkPositionIndex(int index, int size, String message) {
        if (index >= 0 && index <= size) {
            return index;
        } else {
            throw new IllegalArgumentException("InvalidPositionIndex:" + message + ",index=" + index + ",size=" + size);
        }
    }

    public static void checkEquals(Object v1, Object v2) {
        if (!Objects.equals(v1, v2))
            throw new IllegalArgumentException("NotEquals:expected=" + v1 + ",actual=" + v2);
    }

    public static <T> T notNull(T value, String message) {
        if (value == null)
            throw new IllegalArgumentException("IsNull:" + message);
        return value;
    }

    public static <T> T notEmpty(T value, String message) {
        if (ApiStringHelper.isEmptyObject(value))
            throw new IllegalArgumentException("IsEmpty:" + message);
        return value;
    }

    public static int positiveInt(int value, String message) {
        if (value <= 0)
            throw new IllegalArgumentException("NonPositive:" + message + ",value=" + value);
        return value;
    }

    public static long positiveLong(long value, String message) {
        if (value <= 0)
            throw new IllegalArgumentException("NonPositive:" + message + ",value=" + value);
        return value;
    }

    public static int nonNegativeInt(int value, String message) {
        if (value < 0)
            throw new IllegalArgumentException("Negative:" + message + ",value=" + value);
        return value;
    }

    public static long nonNegativeLong(long value, String message) {
        if (value < 0)
            throw new IllegalArgumentException("Negative:" + message + ",value=" + value);
        return value;
    }

    /**
     * Ensures that a range given by its first (inclusive) and last (exclusive) elements fits an array of given length.
     *
     * <p>This method may be used whenever an array range check is needed.
     *
     * @param arrayLength an array length.
     * @param from        a start index (inclusive).
     * @param to          an end index (inclusive).
     * @throws IllegalArgumentException       if {@code from} is greater than {@code to}.
     * @throws ArrayIndexOutOfBoundsException if {@code from} or {@code to} are greater than {@code arrayLength} or negative.
     */
    public static void checkFromTo(final int arrayLength, final int from, final int to) {
        if (from < 0) throw new ArrayIndexOutOfBoundsException("Start index (" + from + ") is negative");
        if (from > to)
            throw new IllegalArgumentException("Start index (" + from + ") is greater than end index (" + to + ")");
        if (to > arrayLength)
            throw new ArrayIndexOutOfBoundsException("End index (" + to + ") is greater than array length (" + arrayLength + ")");
    }

    /**
     * Ensures that a range given by an offset and a length fits an array of given length.
     *
     * <p>This method may be used whenever an array range check is needed.
     *
     * @param arrayLength an array length.
     * @param offset      a start index for the fragment
     * @param length      a length (the number of elements in the fragment).
     * @throws IllegalArgumentException       if {@code length} is negative.
     * @throws ArrayIndexOutOfBoundsException if {@code offset} is negative or {@code offset}+{@code length} is greater than {@code arrayLength}.
     */
    public static void checkOffsetLength(final int arrayLength, final int offset, final int length) {
        if (offset < 0) throw new ArrayIndexOutOfBoundsException("Offset (" + offset + ") is negative");
        if (length < 0) throw new IllegalArgumentException("Length (" + length + ") is negative");
        if (offset + length > arrayLength)
            throw new ArrayIndexOutOfBoundsException("Last index (" + (offset + length) + ") is greater than array length (" + arrayLength + ")");
    }
}