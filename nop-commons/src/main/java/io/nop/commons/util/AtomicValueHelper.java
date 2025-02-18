package io.nop.commons.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class AtomicValueHelper {

    public static <T> int get(T stat, AtomicIntegerFieldUpdater<T> updater, boolean reset) {
        if (reset) {
            return updater.getAndSet(stat, 0);
        } else {
            return updater.get(stat);
        }
    }

    public static <T> long get(T stat, AtomicLongFieldUpdater<T> updater, boolean reset) {
        if (reset) {
            return updater.getAndSet(stat, 0);
        } else {
            return updater.get(stat);
        }
    }

    public static long get(AtomicLong counter, boolean reset) {
        if (reset) {
            return counter.getAndSet(0);
        } else {
            return counter.get();
        }
    }

    public static int get(AtomicInteger counter, boolean reset) {
        if (reset) {
            return counter.getAndSet(0);
        } else {
            return counter.get();
        }
    }
}
