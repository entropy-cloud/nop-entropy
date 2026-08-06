package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Batch runner for the checkpoint decision test framework (design
 * {@code guardrail-contract.md} §增量 4). Loads (or accepts) a list of
 * {@link CheckpointTestCase}s, runs each one through a
 * {@link CheckpointTestHarness}, and aggregates the per-case
 * {@link CheckpointTestResult}s into a {@link CheckpointTestReport}.
 *
 * <p>The end-to-end data flow exercised by this runner:
 * <pre>
 *   YAML corpus ─Loader─▶ List&lt;CheckpointTestCase&gt;
 *      ─Runner─▶ CheckpointTestHarness.runCase (per case)
 *                   └─▶ AgentSecurityConsultation.buildCheckpointChain()
 *                         └─▶ SecurityCheckpointChain.evaluate()  (real chain)
 *                               └─▶ CollectingAuditLogger captures matchedRule
 *      ─Runner─▶ CheckpointTestReport (per-category + per-matchedRule metrics)
 * </pre>
 *
 * <p><b>No silent skip</b>: a case whose {@code runCase} records a failure
 * (e.g. {@code evaluate()} threw) is still included in the report as a failed
 * result — it is never silently dropped. {@code null} cases are rejected
 * fail-loud.
 */
public class CheckpointTestRunner {

    private final CheckpointTestHarness harness;

    public CheckpointTestRunner() {
        this(new CheckpointTestHarness());
    }

    public CheckpointTestRunner(CheckpointTestHarness harness) {
        if (harness == null) {
            throw new NopAiAgentException("CheckpointTestRunner: harness must not be null");
        }
        this.harness = harness;
    }

    /**
     * Run a batch of cases and aggregate the results into a report.
     *
     * @param cases the cases to run (must not be null; may be empty, yielding
     *              an empty report)
     * @return the aggregated report (never {@code null})
     */
    public CheckpointTestReport run(List<CheckpointTestCase> cases) {
        if (cases == null) {
            throw new NopAiAgentException("CheckpointTestRunner.run: cases must not be null");
        }
        List<CheckpointTestResult> results = new ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            CheckpointTestCase tc = cases.get(i);
            if (tc == null) {
                throw new NopAiAgentException(
                        "CheckpointTestRunner.run: case at index " + i + " is null");
            }
            results.add(harness.runCase(tc));
        }
        return CheckpointTestReport.build(Collections.unmodifiableList(results));
    }

    /**
     * Convenience: load the corpus from a VFS directory and run it in one call.
     */
    public CheckpointTestReport runDirectory(String vfsDirPath) {
        return run(CheckpointTestCaseLoader.loadDirectory(vfsDirPath));
    }

    public CheckpointTestHarness getHarness() {
        return harness;
    }
}
