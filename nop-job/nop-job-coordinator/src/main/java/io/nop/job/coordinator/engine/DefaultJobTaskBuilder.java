package io.nop.job.coordinator.engine;

import io.nop.dao.api.IDaoProvider;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class DefaultJobTaskBuilder implements IJobTaskBuilder {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJobTaskBuilder.class);

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        NopJobTask task = daoProvider.daoFor(NopJobTask.class).newEntity();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(1);
        task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);

        // AR-91: leave workerInstanceId NULL in single mode. The task is then claimable by any worker
        // via the competing-consumer IS-NULL branch, including under enforceAttribution=true on a
        // non-co-deployed worker (previously the coordinator hostId was written here, which under
        // enforceAttribution != worker hostId and non-NULL starved every single task forever).
        // After a worker claims the task, tryLockTasksForExecute sets workerInstanceId to that worker's
        // hostId, so SUSPICIOUS liveness detection (which only applies to RUNNING tasks) is unaffected.
        task.setWorkerInstanceId(null);

        // 这里可以设置专门针对task的参数，它们将和fire中的参数合并，最终成为执行参数
        task.setTaskPayload(null);
        task.setPartitionIndex(fire.getPartitionIndex());

        return Collections.singletonList(task);
    }
}
