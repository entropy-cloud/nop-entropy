package io.nop.xlang.debugger;

import io.nop.api.core.time.IClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可推进时钟：时间只在测试显式推进时前进；按线程统计 {@link #currentTimeMillis()} 的读取次数，
 * 供测试确认某工作线程已在推进后的时刻读过时钟（如已进入 deadline 检查、已重新求值租约谓词）。
 * 经 {@code CoreMetrics.registerClock} 逐测试注册，测试结束须恢复 {@code CoreMetrics.defaultClock()}。
 */
public final class ManualClock implements IClock {
    private final AtomicLong millis;
    private final Map<Thread, AtomicLong> readsByThread = new ConcurrentHashMap<>();

    public ManualClock(long initialMillis) {
        this.millis = new AtomicLong(initialMillis);
    }

    public static ManualClock startingNow() {
        return new ManualClock(System.currentTimeMillis());
    }

    public void advance(long deltaMillis) {
        if (deltaMillis < 0)
            throw new IllegalArgumentException("clock cannot move backwards: " + deltaMillis);
        millis.addAndGet(deltaMillis);
    }

    /** 当前虚拟时间，不计入读取次数。 */
    public long peekMillis() {
        return millis.get();
    }

    public long readsBy(Thread thread) {
        AtomicLong count = readsByThread.get(thread);
        return count == null ? 0 : count.get();
    }

    @Override
    public long currentTimeMillis() {
        readsByThread.computeIfAbsent(Thread.currentThread(), t -> new AtomicLong()).incrementAndGet();
        return millis.get();
    }

    @Override
    public long nanoTime() {
        return millis.get() * 1_000_000L;
    }

    @Override
    public LocalDate currentDate() {
        return currentDateTime().toLocalDate();
    }

    @Override
    public LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis.get()), ZoneId.systemDefault());
    }
}
