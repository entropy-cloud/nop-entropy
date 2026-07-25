/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.exceptions.StreamException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the unified transition table (G54/G58) shared by {@link Task} and
 * {@link SubtaskTask} via {@link TaskStateTransition}.
 *
 * <p>Coverage requirements (per plan item #24 — "no silent skips"):
 * <ul>
 *   <li>Every legal transition is accepted</li>
 *   <li>Illegal transitions (terminal-state exits, self-loops, cross-stage skips) throw</li>
 * </ul>
 */
class TestTaskStateTransition {

    // ============ Legal transitions ============

    @Test
    void legalCreatedTransitions() {
        assertTrue(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CREATED, TaskStateTransition.StateName.SCHEDULED));
        assertTrue(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CREATED, TaskStateTransition.StateName.CANCELING));
        assertTrue(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CREATED, TaskStateTransition.StateName.FAILED));
    }

    @Test
    void legalScheduledTransitions() {
        for (TaskStateTransition.StateName target : new TaskStateTransition.StateName[]{
                TaskStateTransition.StateName.DEPLOYING,
                TaskStateTransition.StateName.RECOVERING,
                TaskStateTransition.StateName.CANCELING,
                TaskStateTransition.StateName.FAILED}) {
            assertTrue(TaskStateTransition.isLegalTransition(
                    TaskStateTransition.StateName.SCHEDULED, target),
                    "SCHEDULED → " + target + " should be legal");
        }
    }

    @Test
    void legalDeployingTransitions() {
        for (TaskStateTransition.StateName target : new TaskStateTransition.StateName[]{
                TaskStateTransition.StateName.RUNNING,
                TaskStateTransition.StateName.RECOVERING,
                TaskStateTransition.StateName.CANCELING,
                TaskStateTransition.StateName.FAILED}) {
            assertTrue(TaskStateTransition.isLegalTransition(
                    TaskStateTransition.StateName.DEPLOYING, target),
                    "DEPLOYING → " + target + " should be legal");
        }
    }

    @Test
    void legalRunningTransitions() {
        for (TaskStateTransition.StateName target : new TaskStateTransition.StateName[]{
                TaskStateTransition.StateName.COMPLETED,
                TaskStateTransition.StateName.RECOVERING,
                TaskStateTransition.StateName.CANCELING,
                TaskStateTransition.StateName.FAILED}) {
            assertTrue(TaskStateTransition.isLegalTransition(
                    TaskStateTransition.StateName.RUNNING, target),
                    "RUNNING → " + target + " should be legal");
        }
    }

    @Test
    void legalRecoveringTransitions() {
        for (TaskStateTransition.StateName target : new TaskStateTransition.StateName[]{
                TaskStateTransition.StateName.SCHEDULED,
                TaskStateTransition.StateName.CANCELING,
                TaskStateTransition.StateName.FAILED}) {
            assertTrue(TaskStateTransition.isLegalTransition(
                    TaskStateTransition.StateName.RECOVERING, target),
                    "RECOVERING → " + target + " should be legal");
        }
    }

    @Test
    void legalCancelingTransition() {
        assertTrue(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CANCELING, TaskStateTransition.StateName.CANCELED));
    }

    // ============ Illegal transitions (#24 — must throw, not silently skip) ============

    @Test
    void terminalStatesAreAbsorbing() {
        for (TaskStateTransition.StateName terminal : new TaskStateTransition.StateName[]{
                TaskStateTransition.StateName.COMPLETED,
                TaskStateTransition.StateName.FAILED,
                TaskStateTransition.StateName.CANCELED}) {
            for (TaskStateTransition.StateName target : TaskStateTransition.StateName.values()) {
                assertFalse(TaskStateTransition.isLegalTransition(terminal, target),
                        terminal + " → " + target + " must be illegal (terminal absorbing)");
            }
        }
    }

    @Test
    void selfLoopsAreIllegal() {
        for (TaskStateTransition.StateName s : TaskStateTransition.StateName.values()) {
            assertFalse(TaskStateTransition.isLegalTransition(s, s),
                    s + " → " + s + " (self-loop) must be illegal");
        }
    }

    @Test
    void crossStageSkipsAreIllegal() {
        // CREATED → RUNNING (skipping SCHEDULED/DEPLOYING)
        assertFalse(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CREATED, TaskStateTransition.StateName.RUNNING));
        // CREATED → COMPLETED
        assertFalse(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CREATED, TaskStateTransition.StateName.COMPLETED));
        // SCHEDULED → RUNNING (skipping DEPLOYING)
        assertFalse(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.SCHEDULED, TaskStateTransition.StateName.RUNNING));
        // SCHEDULED → COMPLETED
        assertFalse(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.SCHEDULED, TaskStateTransition.StateName.COMPLETED));
        // DEPLOYING → COMPLETED (skipping RUNNING)
        assertFalse(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.DEPLOYING, TaskStateTransition.StateName.COMPLETED));
        // CANCELING → RUNNING (recovery from cancel not allowed)
        assertFalse(TaskStateTransition.isLegalTransition(
                TaskStateTransition.StateName.CANCELING, TaskStateTransition.StateName.RUNNING));
    }

    @Test
    void validateTransitionThrowsOnIllegal() {
        assertThrows(StreamException.class, () ->
                TaskStateTransition.validateTransition(
                        TaskStateTransition.StateName.COMPLETED,
                        TaskStateTransition.StateName.RUNNING));
        assertThrows(StreamException.class, () ->
                TaskStateTransition.validateTransition(
                        TaskStateTransition.StateName.CREATED,
                        TaskStateTransition.StateName.CREATED));
        assertThrows(StreamException.class, () ->
                TaskStateTransition.validateTransition(
                        TaskStateTransition.StateName.CREATED,
                        TaskStateTransition.StateName.RUNNING));
    }

    // ============ Bridge to enum types ============

    @Test
    void taskStateBridgeMatchesNameBased() {
        assertTrue(TaskStateTransition.isLegalTransition(
                Task.State.CREATED, Task.State.SCHEDULED));
        assertFalse(TaskStateTransition.isLegalTransition(
                Task.State.RUNNING, Task.State.SCHEDULED));
        assertTrue(TaskStateTransition.isTerminal(Task.State.COMPLETED));
        assertFalse(TaskStateTransition.isTerminal(Task.State.RUNNING));
    }

    @Test
    void subtaskTaskStateBridgeMatchesNameBased() {
        assertTrue(TaskStateTransition.isLegalTransition(
                SubtaskTask.State.CREATED, SubtaskTask.State.SCHEDULED));
        assertFalse(TaskStateTransition.isLegalTransition(
                SubtaskTask.State.RUNNING, SubtaskTask.State.SCHEDULED));
        assertTrue(TaskStateTransition.isTerminal(SubtaskTask.State.CANCELED));
        assertFalse(TaskStateTransition.isTerminal(SubtaskTask.State.CANCELING));
    }

    @Test
    void taskStateBridgeThrowsOnIllegal() {
        assertThrows(StreamException.class, () ->
                TaskStateTransition.validateTransition(
                        Task.State.COMPLETED, Task.State.RUNNING));
        assertThrows(StreamException.class, () ->
                TaskStateTransition.validateTransition(
                        SubtaskTask.State.RUNNING, SubtaskTask.State.SCHEDULED));
    }

    @Test
    void taskAndSubtaskShareSameTransitionModel() {
        // Every state name pair must agree between the two enums (G58 unification)
        for (Task.State t : Task.State.values()) {
            for (Task.State t2 : Task.State.values()) {
                boolean taskLegal = TaskStateTransition.isLegalTransition(t, t2);
                SubtaskTask.State s1 = SubtaskTask.State.valueOf(t.name());
                SubtaskTask.State s2 = SubtaskTask.State.valueOf(t2.name());
                boolean subtaskLegal = TaskStateTransition.isLegalTransition(s1, s2);
                assertEquals(taskLegal, subtaskLegal,
                        "Task." + t + "→" + t2 + " legality must equal SubtaskTask equivalent (G58)");
            }
        }
    }
}
