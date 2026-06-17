package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.TaskStepModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link TeamTaskGraphBuilder} and
 * {@link TeamTaskTopology}: DAG model construction, cycle detection (real
 * nop-task {@code GraphStepAnalyzer} integration), and ready/blocked
 * topology queries.
 *
 * <p><b>Wiring verification</b> (Minimum Rules #23): these tests assert that
 * the builder <b>really calls nop-task's graph model API</b> — the returned
 * {@link GraphTaskStepModel} has steps with {@code waitSteps} matching the
 * blockedBy mapping, and cycle detection is delegated to nop-task's
 * {@code GraphStepAnalyzer} (verified by the fact that cyclic input throws).
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 1.
 */
public class TestTeamTaskGraphBuilder {

    // ========================================================================
    // Helpers
    // ========================================================================

    private static TeamTask task(String id, String teamId, List<String> blockedBy,
                                 TeamTaskStatus status) {
        return new TeamTask(id, teamId, "task-" + id, "desc",
                blockedBy, status, "creator", null, System.currentTimeMillis());
    }

    private static TeamTask created(String id, String teamId, String... deps) {
        return task(id, teamId, Arrays.asList(deps), TeamTaskStatus.CREATED);
    }

    private static TeamTask completed(String id, String teamId, String... deps) {
        return task(id, teamId, Arrays.asList(deps), TeamTaskStatus.COMPLETED);
    }

    // ========================================================================
    // Graph model construction + wiring verification
    // ========================================================================

    @Test
    void linearChainBuildsCorrectGraph() {
        TeamTask a = created("A", "t1");
        TeamTask b = created("B", "t1", "A");
        TeamTask c = created("C", "t1", "B");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        GraphTaskStepModel graph = builder.buildGraph(Arrays.asList(a, b, c));

        assertNotNull(graph);
        assertEquals(3, graph.getSteps().size());

        TaskStepModel stepA = graph.getStep("A");
        TaskStepModel stepB = graph.getStep("B");
        TaskStepModel stepC = graph.getStep("C");

        assertNotNull(stepA);
        assertNotNull(stepB);
        assertNotNull(stepC);

        assertTrue(stepA.getWaitSteps() == null || stepA.getWaitSteps().isEmpty(),
                "A has no deps → no waitSteps");
        assertEquals(Set.of("A"), stepB.getWaitSteps(), "B waits on A");
        assertEquals(Set.of("B"), stepC.getWaitSteps(), "C waits on B");

        assertEquals(Set.of("A"), graph.getEnterSteps(), "A is the sole enter step");
        assertEquals(Set.of("C"), graph.getExitSteps(), "C is the sole exit step");
    }

    @Test
    void diamondDependencyBuildsCorrectGraph() {
        TeamTask a = created("A", "t1");
        TeamTask b = created("B", "t1", "A");
        TeamTask c = created("C", "t1", "A");
        TeamTask d = created("D", "t1", "B", "C");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        GraphTaskStepModel graph = builder.buildGraph(Arrays.asList(a, b, c, d));

        assertEquals(4, graph.getSteps().size());

        assertEquals(Set.of("A"), graph.getEnterSteps());
        assertEquals(Set.of("D"), graph.getExitSteps());

        TaskStepModel stepD = graph.getStep("D");
        assertEquals(Set.of("B", "C"), stepD.getWaitSteps(),
                "D waits on both B and C");
    }

    @Test
    void multipleEnterAndExitSteps() {
        TeamTask a = created("A", "t1");
        TeamTask b = created("B", "t1");
        TeamTask c = created("C", "t1", "A", "B");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        GraphTaskStepModel graph = builder.buildGraph(Arrays.asList(a, b, c));

        assertEquals(Set.of("A", "B"), graph.getEnterSteps(),
                "A and B are both enter steps (no deps)");
        assertEquals(Set.of("C"), graph.getExitSteps(),
                "C is the sole exit step (depended upon by nothing)");
    }

    @Test
    void danglingBlockedByReferenceIsIgnoredForEdges() {
        TeamTask a = created("A", "t1", "nonexistent");
        TeamTask b = created("B", "t1");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        GraphTaskStepModel graph = builder.buildGraph(Arrays.asList(a, b));

        TaskStepModel stepA = graph.getStep("A");
        assertTrue(stepA.getWaitSteps() == null || stepA.getWaitSteps().isEmpty(),
                "dangling blockedBy reference to unknown task is not a graph edge");

        assertEquals(Set.of("A", "B"), graph.getEnterSteps(),
                "both A and B are enter steps since A's dep is out-of-set");
    }

    // ========================================================================
    // Cycle detection (real nop-task GraphStepAnalyzer integration)
    // ========================================================================

    @Test
    void cyclicBlockedByIsRejected_fastFailure() {
        TeamTask a = created("A", "t1", "B");
        TeamTask b = created("B", "t1", "A");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> builder.buildGraph(Arrays.asList(a, b)));
        assertTrue(ex.getMessage().contains("cycle"),
                "exception message must mention cycle: " + ex.getMessage());
    }

    @Test
    void selfLoopIsRejected_fastFailure() {
        TeamTask a = created("A", "t1", "A");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();

        assertThrows(NopAiAgentException.class,
                () -> builder.buildGraph(List.of(a)));
    }

    @Test
    void threeNodeCycleIsRejected() {
        TeamTask a = created("A", "t1", "C");
        TeamTask b = created("B", "t1", "A");
        TeamTask c = created("C", "t1", "B");

        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();

        assertThrows(NopAiAgentException.class,
                () -> builder.buildGraph(Arrays.asList(a, b, c)));
    }

    // ========================================================================
    // Empty / NoOp zero-regression (Minimum Rules #24)
    // ========================================================================

    @Test
    void emptyTaskSetThrowsHonestException() {
        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        assertThrows(NopAiAgentException.class,
                () -> builder.buildGraph(Collections.emptyList()));
    }

    @Test
    void emptyTaskSetReturnsNullFromBuildGraphOrEmpty() {
        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        assertEquals(null, builder.buildGraphOrEmpty(Collections.emptyList()));
    }

    @Test
    void nullTaskSetThrowsFromBuildGraph() {
        TeamTaskGraphBuilder builder = new TeamTaskGraphBuilder();
        assertThrows(NopAiAgentException.class,
                () -> builder.buildGraph(null));
    }

    // ========================================================================
    // Topology queries: ready vs blocked
    // ========================================================================

    @Test
    void topologyAllReadyWhenNoDeps() {
        TeamTask a = created("A", "t1");
        TeamTask b = created("B", "t1");

        TeamTaskTopology topo = new TeamTaskTopology(Arrays.asList(a, b));

        assertEquals(2, topo.getReadyTasks().size(), "both tasks are ready (no deps)");
        assertEquals(0, topo.getBlockedTasks().size());
    }

    @Test
    void topologyReadyChangesAsDepsComplete() {
        TeamTask a = created("A", "t1");
        TeamTask b = created("B", "t1", "A");

        TeamTaskTopology topoBefore = new TeamTaskTopology(Arrays.asList(a, b));
        assertEquals(1, topoBefore.getReadyTasks().size(),
                "only A is ready before completion");
        assertEquals("A", topoBefore.getReadyTasks().get(0).getTaskId());
        assertEquals(1, topoBefore.getBlockedTasks().size(),
                "B is blocked (A not COMPLETED)");
        assertEquals("B", topoBefore.getBlockedTasks().get(0).getTaskId());

        TeamTask aDone = completed("A", "t1");
        TeamTask topoB = created("B", "t1", "A");
        TeamTaskTopology topoAfter = new TeamTaskTopology(Arrays.asList(aDone, topoB));
        assertEquals(1, topoAfter.getReadyTasks().size(),
                "B becomes ready after A completes");
        assertEquals("B", topoAfter.getReadyTasks().get(0).getTaskId());
        assertEquals(0, topoAfter.getBlockedTasks().size(),
                "nothing is blocked after A completes");
    }

    @Test
    void topologyDiamondReadyProgression() {
        TeamTask a = created("A", "t1");
        TeamTask b = created("B", "t1", "A");
        TeamTask c = created("C", "t1", "A");
        TeamTask d = created("D", "t1", "B", "C");

        TeamTaskTopology topo = new TeamTaskTopology(Arrays.asList(a, b, c, d));

        assertEquals(1, topo.getReadyTasks().size(), "only A ready initially");
        assertEquals("A", topo.getReadyTasks().get(0).getTaskId());
        assertEquals(3, topo.getBlockedTasks().size(), "B, C, D all blocked");

        TeamTask aDone = completed("A", "t1");
        TeamTask bDone = completed("B", "t1", "A");
        TeamTask topoC = created("C", "t1", "A");
        TeamTask topoD = created("D", "t1", "B", "C");

        TeamTaskTopology topo2 = new TeamTaskTopology(Arrays.asList(aDone, bDone, topoC, topoD));
        assertEquals(1, topo2.getReadyTasks().size(), "only C ready after A,B done");
        assertEquals("C", topo2.getReadyTasks().get(0).getTaskId());
        assertEquals(1, topo2.getBlockedTasks().size(), "D still blocked by C");
    }

    @Test
    void topologyCompletedTasksAreNeitherReadyNorBlocked() {
        TeamTask aDone = completed("A", "t1");
        TeamTask b = created("B", "t1");

        TeamTaskTopology topo = new TeamTaskTopology(Arrays.asList(aDone, b));

        assertEquals(1, topo.getReadyTasks().size(), "only B ready");
        assertEquals(0, topo.getBlockedTasks().size());
        assertEquals(1, topo.getCompletedTasks().size());
        assertEquals("A", topo.getCompletedTasks().get(0).getTaskId());
    }

    @Test
    void topologyEmptyCollectionReturnsEmptyLists() {
        TeamTaskTopology topo = new TeamTaskTopology(Collections.emptyList());
        assertTrue(topo.getReadyTasks().isEmpty());
        assertTrue(topo.getBlockedTasks().isEmpty());
        assertTrue(topo.getCompletedTasks().isEmpty());
    }

    @Test
    void topologyNullCollectionReturnsEmptyLists() {
        TeamTaskTopology topo = new TeamTaskTopology(null);
        assertTrue(topo.getReadyTasks().isEmpty());
        assertTrue(topo.getBlockedTasks().isEmpty());
        assertTrue(topo.getCompletedTasks().isEmpty());
    }
}
