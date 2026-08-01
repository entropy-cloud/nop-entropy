package io.nop.ai.agent.guardrail.rule;

import io.nop.core.model.graph.dag.Dag;
import io.nop.core.model.graph.dag.DagAnalyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * Cycle detector for the {@code dependsOn} graph of a rule set (design
 * {@code guardrail-contract.md} §增量 2, Decision C).
 *
 * <p>Reuses the platform's bottom-layer cycle-detection capability
 * {@link Dag#containsLoop()} (populated by {@link DagAnalyzer#analyze()}), NOT
 * nop-task's {@code GraphStepAnalyzer} (which is tightly bound to the task
 * domain {@code IGraphTaskStepModel} and cannot be reused for guardrail rule
 * graphs).
 *
 * <p>The {@link Dag} requires a single root and that every node be reachable
 * from it (otherwise {@code DagAnalyzer.checkStartReachable()} throws). To
 * satisfy this without altering cycle-detection semantics, a synthetic root
 * pointing to every rule node is added; cycle detection over the real
 * {@code dependsOn} edges is unaffected.
 */
final class DependsOnCycleDetector {

    private DependsOnCycleDetector() {
    }

    /**
     * @return the list of loop edges (each a {@code [from, to]} pair) if the
     *         {@code dependsOn} graph contains a cycle, or an empty list if it
     *         is acyclic. The caller fails loud on a non-empty result.
     */
    static List<List<String>> detectCycleEdges(List<GuardrailRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return new ArrayList<>();
        }
        Dag dag = new Dag(Dag.DEFAULT_ROOT_NAME);
        // add all rule nodes + synthetic root edges so DagAnalyzer can reach every node
        for (GuardrailRule r : rules) {
            dag.addNode(r.getId());
            dag.addNextNode(Dag.DEFAULT_ROOT_NAME, r.getId());
        }
        // add real dependsOn edges (rule -> rule it depends on)
        for (GuardrailRule r : rules) {
            for (String dep : r.getDependsOn()) {
                dag.addNextNode(r.getId(), dep);
            }
        }
        DagAnalyzer analyzer = new DagAnalyzer(dag);
        analyzer.analyze();
        return dag.containsLoop() ? dag.getLoopEdges() : new ArrayList<>();
    }
}
