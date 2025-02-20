package io.nop.core.stat;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.exceptions.ErrorMessageManager;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public abstract class AbstractExecuteStat {
    private long executeLastStartTime;
    private volatile long executeSuccessCount;
    private volatile long executeSpanNanoTotal;
    private volatile long executeSpanNanoMax;
    private volatile int runningCount;
    private volatile int concurrentMax;
    static final AtomicLongFieldUpdater<AbstractExecuteStat> executeSuccessCountUpdater = AtomicLongFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "executeSuccessCount");
    static final AtomicLongFieldUpdater<AbstractExecuteStat> executeSpanNanoTotalUpdater = AtomicLongFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "executeSpanNanoTotal");
    static final AtomicLongFieldUpdater<AbstractExecuteStat> executeSpanNanoMaxUpdater = AtomicLongFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "executeSpanNanoMax");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> runningCountUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "runningCount");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> concurrentMaxUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "concurrentMax");

    private volatile long executeNanoSpanMaxOccurTime;

    private volatile long executeErrorCount;
    private volatile ErrorBean executeErrorLast;
    private volatile long executeErrorLastTime;

    static final AtomicLongFieldUpdater<AbstractExecuteStat> executeErrorCountUpdater = AtomicLongFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "executeErrorCount");

    private volatile long histogram_0_1;
    private volatile long histogram_1_10;
    private volatile int histogram_10_100;
    private volatile int histogram_100_1000;
    private volatile int histogram_1000_10000;
    private volatile int histogram_10000_100000;
    private volatile int histogram_100000_1000000;
    private volatile int histogram_1000000_more;

    static final AtomicLongFieldUpdater<AbstractExecuteStat> histogram_0_1_Updater = AtomicLongFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_0_1");
    static final AtomicLongFieldUpdater<AbstractExecuteStat> histogram_1_10_Updater = AtomicLongFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_1_10");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> histogram_10_100_Updater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_10_100");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> histogram_100_1000_Updater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_100_1000");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> histogram_1000_10000_Updater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_1000_10000");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> histogram_10000_100000_Updater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_10000_100000");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> histogram_100000_1000000_Updater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_100000_1000000");
    static final AtomicIntegerFieldUpdater<AbstractExecuteStat> histogram_1000000_more_Updater = AtomicIntegerFieldUpdater.newUpdater(AbstractExecuteStat.class,
            "histogram_1000000_more");

    public void reset() {
        executeLastStartTime = 0;

        executeSuccessCountUpdater.set(this, 0);
        executeSpanNanoTotalUpdater.set(this, 0);
        executeSpanNanoMaxUpdater.set(this, 0);
        executeNanoSpanMaxOccurTime = 0;
        concurrentMaxUpdater.set(this, 0);

        executeErrorCountUpdater.set(this, 0);
        executeErrorLast = null;
        executeErrorLastTime = 0;

        histogram_0_1_Updater.set(this, 0);
        histogram_1_10_Updater.set(this, 0);
        histogram_10_100_Updater.set(this, 0);
        histogram_100_1000_Updater.set(this, 0);
        histogram_1000_10000_Updater.set(this, 0);
        histogram_10000_100000_Updater.set(this, 0);
        histogram_100000_1000000_Updater.set(this, 0);
        histogram_1000000_more_Updater.set(this, 0);
    }

    public void error(Throwable error) {
        executeErrorCountUpdater.incrementAndGet(this);
        executeErrorLastTime = CoreMetrics.currentTimeMillis();
        executeErrorLast = ErrorMessageManager.instance().buildErrorMessage(null, error, false, false, false);
    }

    public void addExecuteTime(long nanoSpan) {
        executeSpanNanoTotalUpdater.addAndGet(this, nanoSpan);

        for (; ; ) {
            long current = executeSpanNanoMaxUpdater.get(this);
            if (current >= nanoSpan) {
                break;
            }

            if (executeSpanNanoMaxUpdater.compareAndSet(this, current, nanoSpan)) {
                // 可能不准确，但是绝大多数情况下都会正确，性能换取一致性
                executeNanoSpanMaxOccurTime = System.currentTimeMillis();
                break;
            }
        }

        histogramRecord(nanoSpan);
    }

    private void histogramRecord(long nanoSpan) {
        long millis = nanoSpan / 1000 / 1000;

        if (millis < 1) {
            histogram_0_1_Updater.incrementAndGet(this);
        } else if (millis < 10) {
            histogram_1_10_Updater.incrementAndGet(this);
        } else if (millis < 100) {
            histogram_10_100_Updater.incrementAndGet(this);
        } else if (millis < 1000) {
            histogram_100_1000_Updater.incrementAndGet(this);
        } else if (millis < 10000) {
            histogram_1000_10000_Updater.incrementAndGet(this);
        } else if (millis < 100000) {
            histogram_10000_100000_Updater.incrementAndGet(this);
        } else if (millis < 1000000) {
            histogram_100000_1000000_Updater.incrementAndGet(this);
        } else {
            histogram_1000000_more_Updater.incrementAndGet(this);
        }
    }


    public long getExecuteAvgTime() {
        long totalTime = getExecuteSpanNanoTotal();
        long totalCount = getExecuteCount();
        if (totalCount == 0)
            return 0;
        return totalTime / totalCount;
    }

    public void incrementExecuteSuccessCount() {
        executeSuccessCountUpdater.incrementAndGet(this);
    }

    public void incrementRunningCount() {
        int val = runningCountUpdater.incrementAndGet(this);

        for (; ; ) {
            int max = concurrentMaxUpdater.get(this);
            if (val <= max) {
                break;
            }

            if (concurrentMaxUpdater.compareAndSet(this, max, val)) {
                break;
            }
        }
    }

    public void decrementRunningCount() {
        runningCountUpdater.decrementAndGet(this);
    }

    public void decrementExecutingCount() {
        runningCountUpdater.decrementAndGet(this);
    }


    public long getExecuteCount() {
        return getExecuteErrorCount() + getExecuteSuccessCount();
    }


    public long[] getHistogramValues() {
        return new long[]{
                //
                histogram_0_1, //
                histogram_1_10, //
                histogram_10_100, //
                histogram_100_1000, //
                histogram_1000_10000, //
                histogram_10000_100000, //
                histogram_100000_1000000, //
                histogram_1000000_more //
        };
    }

    public long getExecuteLastStartTime() {
        return executeLastStartTime;
    }

    public void setExecuteLastStartTime(long executeLastStartTime) {
        this.executeLastStartTime = executeLastStartTime;
    }

    public long getExecuteSuccessCount() {
        return executeSuccessCount;
    }

    public void setExecuteSuccessCount(long executeSuccessCount) {
        this.executeSuccessCount = executeSuccessCount;
    }

    public long getExecuteSpanNanoTotal() {
        return executeSpanNanoTotal;
    }

    public void setExecuteSpanNanoTotal(long executeSpanNanoTotal) {
        this.executeSpanNanoTotal = executeSpanNanoTotal;
    }

    public long getExecuteSpanNanoMax() {
        return executeSpanNanoMax;
    }

    public void setExecuteSpanNanoMax(long executeSpanNanoMax) {
        this.executeSpanNanoMax = executeSpanNanoMax;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(int runningCount) {
        this.runningCount = runningCount;
    }

    public int getConcurrentMax() {
        return concurrentMax;
    }

    public void setConcurrentMax(int concurrentMax) {
        this.concurrentMax = concurrentMax;
    }

    public long getExecuteNanoSpanMaxOccurTime() {
        return executeNanoSpanMaxOccurTime;
    }

    public void setExecuteNanoSpanMaxOccurTime(long executeNanoSpanMaxOccurTime) {
        this.executeNanoSpanMaxOccurTime = executeNanoSpanMaxOccurTime;
    }

    public long getExecuteErrorCount() {
        return executeErrorCount;
    }

    public void setExecuteErrorCount(long executeErrorCount) {
        this.executeErrorCount = executeErrorCount;
    }

    public ErrorBean getExecuteErrorLast() {
        return executeErrorLast;
    }

    public void setExecuteErrorLast(ErrorBean executeErrorLast) {
        this.executeErrorLast = executeErrorLast;
    }

    public long getExecuteErrorLastTime() {
        return executeErrorLastTime;
    }

    public void setExecuteErrorLastTime(long executeErrorLastTime) {
        this.executeErrorLastTime = executeErrorLastTime;
    }

    public long getHistogram_0_1() {
        return histogram_0_1;
    }

    public void setHistogram_0_1(long histogram_0_1) {
        this.histogram_0_1 = histogram_0_1;
    }

    public long getHistogram_1_10() {
        return histogram_1_10;
    }

    public void setHistogram_1_10(long histogram_1_10) {
        this.histogram_1_10 = histogram_1_10;
    }

    public int getHistogram_10_100() {
        return histogram_10_100;
    }

    public void setHistogram_10_100(int histogram_10_100) {
        this.histogram_10_100 = histogram_10_100;
    }

    public int getHistogram_100_1000() {
        return histogram_100_1000;
    }

    public void setHistogram_100_1000(int histogram_100_1000) {
        this.histogram_100_1000 = histogram_100_1000;
    }

    public int getHistogram_1000_10000() {
        return histogram_1000_10000;
    }

    public void setHistogram_1000_10000(int histogram_1000_10000) {
        this.histogram_1000_10000 = histogram_1000_10000;
    }

    public int getHistogram_10000_100000() {
        return histogram_10000_100000;
    }

    public void setHistogram_10000_100000(int histogram_10000_100000) {
        this.histogram_10000_100000 = histogram_10000_100000;
    }

    public int getHistogram_100000_1000000() {
        return histogram_100000_1000000;
    }

    public void setHistogram_100000_1000000(int histogram_100000_1000000) {
        this.histogram_100000_1000000 = histogram_100000_1000000;
    }

    public int getHistogram_1000000_more() {
        return histogram_1000000_more;
    }

    public void setHistogram_1000000_more(int histogram_1000000_more) {
        this.histogram_1000000_more = histogram_1000000_more;
    }
}
