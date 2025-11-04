package io.nop.commons.concurrent.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

public interface ISemaphore {
    boolean tryAcquire(int permits, long timeout);

    void release(int permits);

    int availablePermits();

    int maxPermits();

    long getAcquireSuccessCount();

    long getAcquireFailCount();

    void resetStats();

    default SemaphoreStats getStats() {
        return new SemaphoreStats(availablePermits(), maxPermits(), getAcquireSuccessCount(), getAcquireFailCount());
    }

    @DataBean
    class SemaphoreStats {
        private final int availablePermits;
        private final int maxPermits;
        private final long acquireCount;
        private final long acquireFailCount;

        public SemaphoreStats(@JsonProperty("availablePermits") int availablePermits,
                              @JsonProperty("maxPermits") int maxPermits,
                              @JsonProperty("acquireCount") long acquireCount,
                              @JsonProperty("acquireFailCount") long acquireFailCount) {

            this.availablePermits = availablePermits;
            this.maxPermits = maxPermits;
            this.acquireCount = acquireCount;
            this.acquireFailCount = acquireFailCount;
        }

        public int getMaxPermits() {
            return maxPermits;
        }

        public int getAvailablePermits() {
            return availablePermits;
        }

        public long getAcquireCount() {
            return acquireCount;
        }

        public long getAcquireFailCount() {
            return acquireFailCount;
        }
    }
}
