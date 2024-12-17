package io.nop.cluster.elector;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;

import java.util.concurrent.TimeUnit;

public abstract class AbstractPollingLeaderElector extends AbstractLeaderElector {

    protected IScheduledExecutor scheduledExecutor;

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    @Override
    protected void doStart() {
        if (scheduledExecutor == null) {
            scheduledExecutor = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
        }

        scheduledExecutor.schedule(this::checkLeader, 0, TimeUnit.MILLISECONDS);
    }

    protected abstract Void checkLeader();
}
