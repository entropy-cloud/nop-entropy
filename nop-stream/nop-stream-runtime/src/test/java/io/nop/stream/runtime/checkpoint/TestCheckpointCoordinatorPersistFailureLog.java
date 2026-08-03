package io.nop.stream.runtime.checkpoint;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.SavepointMetadata;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.storage.CheckpointStorageException;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for plan item P2-7: ensures {@code CheckpointCoordinator.onCompletePersistFailure}
 * emits exactly one log event per failure (the prior implementation emitted both LOG.error and
 * LOG.warn for the same failure). The test captures the {@code CheckpointCoordinator} SLF4J logger
 * via a Logback {@link ListAppender} and asserts the failure message appears exactly once at
 * ERROR level and zero times at WARN level.
 */
class TestCheckpointCoordinatorPersistFailureLog {

    private static final TaskLocation LOC_1 = new TaskLocation("j", "p", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("j", "p", "v2", 2);

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        CheckpointIDCounter counter = new CheckpointIDCounter();
        ICheckpointStorage storage = failingStorage();
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(60_000L)
                .checkpointTimeout(30_000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(5)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("j", "p", counter, storage, config);
        coordinator.setTasksToAcknowledge(Arrays.asList(LOC_1, LOC_2));
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    /**
     * Storage that always throws on {@code storeCheckPoint}, forcing the coordinator down the
     * {@code onCompletePersistFailure} path. The unused overrides return inert defaults so the
     * interface contract is satisfied without dragging in extra dependencies.
     */
    private static ICheckpointStorage failingStorage() {
        return new ICheckpointStorage() {
            @Override public String getName() { return "AlwaysFailingStorage"; }
            @Override
            public String storeCheckPoint(CompletedCheckpoint checkpoint) throws CheckpointStorageException {
                throw new StreamException("simulated storage failure for cp " + checkpoint.getCheckpointId());
            }
            @Override public CompletedCheckpoint getLatestCheckpoint(String jobId, String pipelineId) { return null; }
            @Override public List<CompletedCheckpoint> getAllCheckpoints(String jobId) { return Collections.emptyList(); }
            @Override public List<CompletedCheckpoint> getLatestCheckpoints(String jobId, int count) { return Collections.emptyList(); }
            @Override public void deleteCheckpoint(String jobId, String pipelineId, long checkpointId) { }
            @Override public void deleteAllCheckpoints(String jobId) { }
            @Override public int getCheckpointCount(String jobId) { return 0; }
            @Override public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) { return targetPath; }
            @Override public CompletedCheckpoint loadSavepoint(String savepointPath) { return null; }
            @Override public SavepointMetadata loadSavepointMetadata(String savepointPath) { return null; }
            @Override public void storeEpochManifest(String jobId, String pipelineId, EpochManifest manifest) { }
            @Override public EpochManifest loadLatestEpochManifest(String jobId, String pipelineId) { return null; }
        };
    }

    @Test
    void persistFailureEmitsExactlyOneLogEvent() throws Exception {
        Logger coordLogger = (Logger) LoggerFactory.getLogger(CheckpointCoordinator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        coordLogger.addAppender(appender);
        try {
            PendingCheckpoint cp = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
            assertNotNull(cp);
            coordinator.acknowledgeTask(LOC_1, cp.getCheckpointId(), TaskStateSnapshot.empty(LOC_1));
            coordinator.acknowledgeTask(LOC_2, cp.getCheckpointId(), TaskStateSnapshot.empty(LOC_2));

            assertEquals(PendingCheckpoint.Status.FAILED, cp.getStatus().get(),
                    "storage failure must propagate to FAILED status");

            long failedMessages = appender.list.stream()
                    .filter(ev -> ev.getFormattedMessage() != null
                            && ev.getFormattedMessage().contains("Failed checkpoint"))
                    .count();
            assertEquals(1, failedMessages,
                    "onCompletePersistFailure must log the failure exactly once (P2-7 dedup). Events: "
                            + appender.list);

            long errorCount = appender.list.stream()
                    .filter(ev -> ev.getLevel() == Level.ERROR)
                    .filter(ev -> ev.getFormattedMessage() != null
                            && ev.getFormattedMessage().contains("Failed checkpoint"))
                    .count();
            assertEquals(1, errorCount, "failure should be logged once at ERROR level");

            long warnCount = appender.list.stream()
                    .filter(ev -> ev.getLevel() == Level.WARN)
                    .filter(ev -> ev.getFormattedMessage() != null
                            && ev.getFormattedMessage().contains("Failed checkpoint"))
                    .count();
            assertEquals(0, warnCount,
                    "failure must NOT also be logged at WARN level (duplicate logging removed by P2-7). Events: "
                            + appender.list);
        } finally {
            coordLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
