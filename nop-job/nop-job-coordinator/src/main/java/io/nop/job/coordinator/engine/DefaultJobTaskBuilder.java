package io.nop.job.coordinator.engine;

import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultJobTaskBuilder implements IJobTaskBuilder {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJobTaskBuilder.class);

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        long now = System.currentTimeMillis();

        NopJobTask task = new NopJobTask();
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

        // targetHost is reserved for future use: populate when a naming service
        // is available to resolve workerInstanceId to a network address.
        task.getTaskPayloadComponent().set_jsonValue(Map.of(
                "jobFireId", fire.getJobFireId(),
                "jobParamsSnapshot", emptyIfNull(fire.getJobParamsSnapshotComponent().get_jsonMap())
        ));
        task.setPartitionIndex(fire.getPartitionIndex());
        task.setCreatedBy("system");
        task.setCreateTime(new Timestamp(now));
        task.setUpdatedBy("system");
        task.setUpdateTime(new Timestamp(now));

        return Collections.singletonList(task);
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }
}
