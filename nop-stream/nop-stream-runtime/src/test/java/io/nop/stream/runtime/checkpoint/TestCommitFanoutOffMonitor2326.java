/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.core.testsupport.TestAwait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 CC-01 regression pin (plan 2326): participant commit fan-out (sink
 * finishCommit I/O) and failed-commit retries must run on the dedicated
 * commit-fanout executor — NOT under the coordinator monitor. The pre-fix code
 * invoked {@code retryFailedCommits} + {@code notifyParticipantsFinishCommit}
 * inline inside {@code onCompletePersistSuccess} (a synchronized method), so a
 * slow sink serialized the whole control plane behind it
 * ({@code tryTriggerPendingCheckpoint}/{@code acknowledgeTask} all share the
 * same monitor).
 *
 * <p>时间确定化（plan 2510）：模拟慢提交改为 latch 阻塞（由测试显式放行），「慢提交
 * 在途窗口」不再是墙钟 sleep；monitor 不被占用的证明是结构性事实——探针在
 * latch 未放行（提交确定性仍在途）时已返回。变异探针语义不变：把扇出挪回
 * monitor 内联，探针必须等 latch 放行才能拿到 monitor → slowCommitFinished 变
 * true → 探针前置断言失败变红。
 */
class TestCommitFanoutOffMonitor2326 {

    private static final String JOB_ID = "cc01-job";
    private static final String PIPELINE_ID = "pipeline-0";
    private static final TaskLocation LOC_1 = new TaskLocation(JOB_ID, PIPELINE_ID, "src", 0);
    private static final TaskLocation LOC_2 = new TaskLocation(JOB_ID, PIPELINE_ID, "sink", 0);

    @TempDir
    Path tempDir;

    private static CheckpointConfig config() {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(3600_000L);
        config.setStorageType("local");
        return config;
    }

    @Test
    void slowParticipantFinishCommitDoesNotBlockMonitor(@TempDir Path dir) throws Exception {
        CheckpointCoordinator coord = new CheckpointCoordinator(JOB_ID, PIPELINE_ID,
                new CheckpointIDCounter(), new LocalFileCheckpointStorage(dir.toString()), config());
        coord.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));

        AtomicBoolean slowCommitStarted = new AtomicBoolean(false);
        AtomicBoolean slowCommitFinished = new AtomicBoolean(false);
        // Simulated slow sink commit I/O (file fsync / JDBC commit): the commit parks
        // on this latch until the test releases it — the in-flight window is
        // deterministic, not a wall-clock sleep.
        CountDownLatch releaseCommit = new CountDownLatch(1);
        coord.addParticipant(new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return TaskStateSnapshot.empty(LOC_1);
            }

            @Override
            public void prepareCommit(long epochId) {
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
                slowCommitStarted.set(true);
                try {
                    releaseCommit.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                slowCommitFinished.set(true);
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
            }
        });

        try {
            PendingCheckpoint pending = coord.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(pending);
            coord.acknowledgeTask(LOC_1, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coord.acknowledgeTask(LOC_2, pending.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            // Wait until the fan-out task has ENTERED the (latch-parked) slow commit —
            // from this point the monitor is (post-fix) free even though the commit
            // cannot finish.
            TestAwait.until("fan-out must reach the participant commit",
                    slowCommitStarted::get);

            // Monitor probe: ONE lightweight synchronized call. Pre-fix (inline fan-out
            // under the monitor) this call could only acquire the monitor after the
            // slow finishCommit returned (latch released → slowCommitFinished=true);
            // post-fix it returns while the commit is deterministically still parked.
            coord.registerTask(LOC_1);
            coord.unregisterTask(LOC_1);
            assertEquals(1, releaseCommit.getCount(),
                    "probe precondition: the slow commit is still parked on the latch");
            assertTrue(!slowCommitFinished.get(),
                    "probe returned while the slow commit was still in flight — commit fan-out "
                            + "is not running under the monitor (CC-01 regression probe)");

            releaseCommit.countDown();
        } finally {
            coord.shutdown();
            TestAwait.until("shutdown grace must drain the queued slow commit",
                    slowCommitFinished::get);
            assertTrue(slowCommitFinished.get(),
                    "shutdown grace must drain the queued slow commit");
        }
    }
}
