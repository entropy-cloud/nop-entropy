package io.nop.job.coordinator.engine;

import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobTask;

import java.util.List;

/**
 * Builds task(s) from a fire event. The default implementation creates a single task.
 * Custom implementations (e.g., for broadcast RPC) can create multiple tasks.
 */
public interface IJobTaskBuilder {
    /**
     * Build one or more tasks for the given fire event.
     * Each task should have taskNo, taskStatus, taskPayload, partitionIndex, etc. set.
     */
    List<NopJobTask> buildTasks(NopJobFire fire);
}
