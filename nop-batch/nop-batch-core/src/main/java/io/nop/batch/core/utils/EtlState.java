package io.nop.batch.core.utils;

import io.nop.api.core.annotations.data.DataBean;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

@DataBean
public class EtlState {
    private volatile long insertCount;
    private volatile long updateCount;

    static final AtomicLongFieldUpdater<EtlState> insertCountUpdater = AtomicLongFieldUpdater.newUpdater(EtlState.class,
            "insertCount");

    static final AtomicLongFieldUpdater<EtlState> updateCountUpdater = AtomicLongFieldUpdater.newUpdater(EtlState.class,
            "updateCount");

    public long addUpdateCount(int delta) {
        return updateCountUpdater.addAndGet(this, delta);
    }

    public long addInsertCount(int delta) {
        return insertCountUpdater.addAndGet(this, delta);
    }

    public long getInsertCount() {
        return insertCount;
    }

    public void setInsertCount(long insertCount) {
        this.insertCount = insertCount;
    }

    public long getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(long updateCount) {
        this.updateCount = updateCount;
    }
}
