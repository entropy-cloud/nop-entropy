package io.nop.ai.agent.team.scheduler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-07/C4-013 regression (plan 2496 Phase 4, 30-pt table #25): the periodic
 * team-task scheduling scan must survive a Throwable (incl. Error) thrown by the
 * scan body. Pre-fix {@code scanOnceSafe} caught only {@code RuntimeException} —
 * an Error escaped the task body and the JDK {@code scheduleWithFixedDelay} round
 * was cancelled permanently and silently (team-task scheduling stops; the failure
 * vanishes into the discarded ScheduledFuture). Post-fix the guard catches
 * Throwable, keeps the chain re-armable and keeps the failure observable.
 */
public class TestTeamTaskSchedulerDaemonScanInsulation {

    /** Delegating store that injects a deterministic Error on the first ready query. */
    static final class ErrorInjectingStore implements ITeamTaskStore {
        private final ITeamTaskStore delegate;
        private final AtomicBoolean errorArmed = new AtomicBoolean(false);
        private final AtomicBoolean errorFired = new AtomicBoolean(false);

        ErrorInjectingStore(ITeamTaskStore delegate) {
            this.delegate = delegate;
        }

        void armError() {
            errorArmed.set(true);
        }

        boolean errorFired() {
            return errorFired.get();
        }

        private List<TeamTask> guard(List<TeamTask> result) {
            if (errorArmed.get() && errorFired.compareAndSet(false, true)) {
                throw new LinkageError("injected scan Error");
            }
            return result;
        }

        @Override
        public TeamTask createTask(String teamId, String subject, String description,
                                   List<String> blockedBy, String createdBy) {
            return delegate.createTask(teamId, subject, description, blockedBy, createdBy);
        }

        @Override
        public Optional<TeamTask> getTask(String taskId) {
            return delegate.getTask(taskId);
        }

        @Override
        public List<TeamTask> getTasksByTeam(String teamId) {
            return guard(delegate.getTasksByTeam(teamId));
        }

        @Override
        public List<TeamTask> getTasksByCreator(String createdBy) {
            return delegate.getTasksByCreator(createdBy);
        }

        @Override
        public Optional<TeamTask> claimTask(String taskId, String claimedBy) {
            return delegate.claimTask(taskId, claimedBy);
        }

        @Override
        public Optional<TeamTask> completeTask(String taskId, String completedBy, Long claimEpoch) {
            return delegate.completeTask(taskId, completedBy, claimEpoch);
        }

        @Override
        public Optional<TeamTask> abandonTask(String taskId, String abandonedBy, Long claimEpoch) {
            return delegate.abandonTask(taskId, abandonedBy, claimEpoch);
        }

        @Override
        public Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
            return delegate.reclaimTask(taskId, reclaimedBy);
        }
    }

    private RecordingSchedulerAdapter scheduler;
    private TestTeamTaskSchedulerDaemon.RecordingAgentEngine engine;
    private InMemoryTeamManager teamManager;
    private ErrorInjectingStore store;
    private TeamTaskSchedulerDaemon daemon;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger daemonLogger;

    @BeforeEach
    void setUp() {
        engine = new TestTeamTaskSchedulerDaemon.RecordingAgentEngine();
        teamManager = new InMemoryTeamManager();
        store = new ErrorInjectingStore(new InMemoryTeamTaskStore());
        scheduler = new RecordingSchedulerAdapter();
        daemon = new TeamTaskSchedulerDaemon(engine, store, teamManager, scheduler);

        TeamSpec spec = new TeamSpec("daemon-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = teamManager.createTeam(spec);
        teamManager.bindMemberSession(team.getTeamId(), "worker", "worker-session", "actor-worker");
        store.createTask(team.getTeamId(), "A", "desc-A", Collections.emptyList(), "lead-session");

        daemonLogger = (Logger) LoggerFactory.getLogger(TeamTaskSchedulerDaemon.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        daemonLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (daemon != null) {
            daemon.stop();
        }
        if (logAppender != null && daemonLogger != null) {
            daemonLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    void testScanChainSurvivesInjectedErrorAndStaysObservable() {
        daemon.start();
        Runnable round = scheduler.lastCommand;
        assertTrue(round != null, "start must schedule the periodic scan");

        store.armError();

        // Round 1 with injected Error: pre-fix the LinkageError propagated out of the
        // task body (killing the JDK periodic round); post-fix it is caught + observed.
        assertDoesNotThrow(round::run, "the scan task body must not let an Error escape");
        assertTrue(store.errorFired(), "the injected Error must actually have been reached");

        long warns = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("periodic scan failed"))
                .count();
        assertTrue(warns >= 1, "the failed scan round must be observable via the warn log");

        // Round 2: the chain is still re-armable — the scan runs again (this time the
        // store passes and the scan proceeds normally).
        assertDoesNotThrow(round::run, "the periodic chain must remain alive after an Error round");
    }

    /**
     * Minimal recording scheduler (same shape as TestTeamTaskSchedulerDaemon's
     * RecordingScheduler) exposing the periodic command for manual round runs.
     */
    static final class RecordingSchedulerAdapter implements IScheduledExecutor {
        Runnable lastCommand;

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                TimeUnit unit) {
            this.lastCommand = command;
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                             TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public String getName() {
            return "test-daemon-scan-recording-scheduler";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
