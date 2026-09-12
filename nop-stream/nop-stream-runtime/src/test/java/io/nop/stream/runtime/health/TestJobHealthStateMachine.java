/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.health;

import io.nop.stream.core.exceptions.StreamException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 16 (P-REQ-7): transition legality table + listener dispatch + fail-fast
 * on illegal transitions (acceptance: 状态枚举 + 迁移合法性表落码且有单测).
 */
class TestJobHealthStateMachine {

    private static final class RecordingListener implements JobHealthListener {
        final List<String> transitions = new ArrayList<>();

        @Override
        public void onHealthTransition(String jobId, StreamJobHealth from, StreamJobHealth to, String cause) {
            transitions.add(from + "->" + to + "(" + cause + ")");
        }
    }

    @Test
    void legalLifecycleTransitionsSucceedAndDispatchListeners() {
        RecordingListener listener = new RecordingListener();
        JobHealthStateMachine machine = new JobHealthStateMachine("job-1");
        machine.addListener(listener);

        assertEquals(StreamJobHealth.CREATED, machine.getCurrent());
        machine.onStart();
        assertEquals(StreamJobHealth.RUNNING, machine.getCurrent());

        machine.onRecoveryStarted(1);
        assertEquals(StreamJobHealth.RECOVERING, machine.getCurrent());

        machine.onRecoveryCompleted(1);
        assertEquals(StreamJobHealth.DEGRADED, machine.getCurrent());

        // durable checkpoint heals DEGRADED -> RUNNING
        assertEquals(StreamJobHealth.RUNNING, machine.onDurableCheckpoint(7L));
        assertEquals(StreamJobHealth.RUNNING, machine.getCurrent());

        // second recovery from DEGRADED via RUNNING: RUNNING -> RECOVERING -> DEGRADED -> CANCELED
        machine.onRecoveryStarted(2);
        machine.onRecoveryCompleted(2);
        assertEquals(StreamJobHealth.DEGRADED, machine.getCurrent());
        machine.onCanceled();
        assertEquals(StreamJobHealth.CANCELED, machine.getCurrent());

        assertEquals(List.of(
                "CREATED->RUNNING(start)",
                "RUNNING->RECOVERING(globalRecovery#1)",
                "RECOVERING->DEGRADED(recovery-completed(restarts=1))",
                "DEGRADED->RUNNING(durable-checkpoint-7)",
                "RUNNING->RECOVERING(globalRecovery#2)",
                "RECOVERING->DEGRADED(recovery-completed(restarts=2))",
                "DEGRADED->CANCELED(terminate(CANCEL))"),
                listener.transitions);
    }

    @Test
    void restartCapExhaustionPathIsLegal() {
        JobHealthStateMachine machine = new JobHealthStateMachine("job-cap");
        machine.onStart();
        machine.onRecoveryStarted(1);
        machine.onFailJob("Global restart cap exceeded");
        assertEquals(StreamJobHealth.FAILED, machine.getCurrent());
        assertTrue(machine.getCurrent().isTerminal());
    }

    @Test
    void drainAndSuspendFinishFromRunningAndDegraded() {
        JobHealthStateMachine drained = new JobHealthStateMachine("job-drain");
        drained.onStart();
        drained.onFinished("DRAIN");
        assertEquals(StreamJobHealth.FINISHED, drained.getCurrent());

        JobHealthStateMachine suspended = new JobHealthStateMachine("job-suspend");
        suspended.onStart();
        suspended.onRecoveryStarted(1);
        suspended.onRecoveryCompleted(1);
        suspended.onFinished("SUSPEND");
        assertEquals(StreamJobHealth.FINISHED, suspended.getCurrent());
    }

    @Test
    void illegalTransitionsFailFast() {
        JobHealthStateMachine machine = new JobHealthStateMachine("job-illegal");
        // CREATED -> anything but RUNNING
        assertThrows(StreamException.class, () -> machine.transition(StreamJobHealth.FAILED, "x"));
        assertThrows(StreamException.class, () -> machine.transition(StreamJobHealth.DEGRADED, "x"));
        machine.onStart();

        // RUNNING -> DEGRADED is not in the table (DEGRADED only via RECOVERING)
        assertThrows(StreamException.class, () -> machine.transition(StreamJobHealth.DEGRADED, "x"));
        // self-transition is illegal
        assertThrows(StreamException.class, () -> machine.transition(StreamJobHealth.RUNNING, "x"));
        // RUNNING -> DEGRADED jump and RUNNING -> CREATED rewind
        assertThrows(StreamException.class, () -> machine.transition(StreamJobHealth.CREATED, "x"));

        machine.onRecoveryStarted(1);
        // RECOVERING -> CANCELED / FINISHED are illegal (stop must wait for recovery)
        assertThrows(StreamException.class, machine::onCanceled);
        assertThrows(StreamException.class, () -> machine.onFinished("DRAIN"));
        // RECOVERING -> RUNNING without a completed recovery is illegal
        assertThrows(StreamException.class, () -> machine.transition(StreamJobHealth.RUNNING, "x"));

        machine.onRecoveryCompleted(1);
        // terminal-state resurrection is illegal everywhere
        assertTrue(failedMachine().isTerminal());
        assertTrue(canceledMachine().isTerminal());
        assertTrue(finishedMachine().isTerminal());
    }

    private StreamJobHealth failedMachine() {
        JobHealthStateMachine m = new JobHealthStateMachine("t-failed");
        m.onStart();
        m.onFailJob("boom");
        assertEquals(StreamJobHealth.FAILED, m.getCurrent());
        assertThrows(StreamException.class, () -> m.transition(StreamJobHealth.RUNNING, "resurrect"));
        return m.getCurrent();
    }

    private StreamJobHealth canceledMachine() {
        JobHealthStateMachine m = new JobHealthStateMachine("t-canceled");
        m.onStart();
        m.onCanceled();
        assertEquals(StreamJobHealth.CANCELED, m.getCurrent());
        assertThrows(StreamException.class, () -> m.transition(StreamJobHealth.FAILED, "late-fail"));
        return m.getCurrent();
    }

    private StreamJobHealth finishedMachine() {
        JobHealthStateMachine m = new JobHealthStateMachine("t-finished");
        m.onStart();
        m.onFinished("DRAIN");
        assertEquals(StreamJobHealth.FINISHED, m.getCurrent());
        assertThrows(StreamException.class, () -> m.transition(StreamJobHealth.RECOVERING, "resurrect"));
        return m.getCurrent();
    }

    @Test
    void durableCheckpointIsNoOpOutsideDegraded() {
        JobHealthStateMachine machine = new JobHealthStateMachine("job-cp");
        machine.onStart();
        // RUNNING: checkpoint completion does not change health (already healthy)
        assertSame(StreamJobHealth.RUNNING, machine.onDurableCheckpoint(1L));
        assertEquals(StreamJobHealth.RUNNING, machine.getCurrent());

        // terminal: still no transition
        machine.onFailJob("boom");
        assertSame(StreamJobHealth.FAILED, machine.onDurableCheckpoint(2L));
        assertEquals(StreamJobHealth.FAILED, machine.getCurrent());
    }

    @Test
    void staticTableMatchesDesign() {
        // observability-design §3.6, the authoritative table
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.CREATED, StreamJobHealth.RUNNING));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.RUNNING, StreamJobHealth.RECOVERING));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.RUNNING, StreamJobHealth.FAILED));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.RUNNING, StreamJobHealth.CANCELED));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.RUNNING, StreamJobHealth.FINISHED));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.RECOVERING, StreamJobHealth.DEGRADED));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.RECOVERING, StreamJobHealth.FAILED));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.DEGRADED, StreamJobHealth.RUNNING));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.DEGRADED, StreamJobHealth.RECOVERING));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.DEGRADED, StreamJobHealth.CANCELED));
        assertTrue(JobHealthStateMachine.isTransitionAllowed(StreamJobHealth.DEGRADED, StreamJobHealth.FINISHED));

        // exhaustive illegal set
        for (StreamJobHealth from : StreamJobHealth.values()) {
            for (StreamJobHealth to : StreamJobHealth.values()) {
                boolean legal = switch (from) {
                    case CREATED -> to == StreamJobHealth.RUNNING;
                    case RUNNING -> to == StreamJobHealth.RECOVERING || to == StreamJobHealth.FAILED
                            || to == StreamJobHealth.CANCELED || to == StreamJobHealth.FINISHED;
                    case RECOVERING -> to == StreamJobHealth.DEGRADED || to == StreamJobHealth.FAILED;
                    case DEGRADED -> to == StreamJobHealth.RUNNING || to == StreamJobHealth.RECOVERING
                            || to == StreamJobHealth.CANCELED || to == StreamJobHealth.FINISHED;
                    default -> false; // FAILED/CANCELED/FINISHED terminal
                };
                assertEquals(legal, JobHealthStateMachine.isTransitionAllowed(from, to),
                        from + " -> " + to);
            }
        }
    }

    @Test
    void listenerFailureIsContained() {
        JobHealthStateMachine machine = new JobHealthStateMachine("job-listener");
        machine.addListener((jid, from, to, cause) -> {
            throw new IllegalStateException("listener bug");
        });
        RecordingListener healthy = new RecordingListener();
        machine.addListener(healthy);
        machine.onStart();
        assertEquals(StreamJobHealth.RUNNING, machine.getCurrent());
        assertEquals(1, healthy.transitions.size());
        assertEquals(2, machine.getListenerCount());
        assertFalse(healthy.transitions.isEmpty());
    }

    @Test
    void nullListenerRegistrationIsIgnored() {
        JobHealthStateMachine machine = new JobHealthStateMachine("job-null");
        machine.addListener(null);
        assertEquals(0, machine.getListenerCount());
        // machine still works
        machine.onStart();
        assertEquals(StreamJobHealth.RUNNING, machine.getCurrent());
    }
}
