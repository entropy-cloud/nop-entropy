package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test-time orchestrator (design {@code guardrail-contract.md} §增量 1,
 * Decision G). Drives the full pipeline:
 *
 * <pre>
 * AttackPlugin (cases)  ──┐
 * AttackTransform (variants) ─┴──&gt; IContentGuardrail.check() ---&gt; GuardrailGrader ---&gt; GuardrailTestReport
 * </pre>
 *
 * <p>This is a <b>test-time</b> component: it never invokes a real LLM or a real
 * agent session. It only calls {@link IContentGuardrail#check}, which is a pure
 * functional inspection of the content. The runtime execution path
 * ({@code AgentPromptAssembly}) never references this class.
 *
 * <p><b>No silent no-op</b>: if the guardrail returns {@code null} or throws,
 * the orchestrator fails loud (it does not silently skip the case), per
 * Minimum Rules #24.
 */
public class GuardrailTestSuite {

    private final GuardrailGrader grader;

    public GuardrailTestSuite() {
        this(new DefaultGuardrailGrader());
    }

    public GuardrailTestSuite(GuardrailGrader grader) {
        if (grader == null) {
            throw new NopAiAgentException("GuardrailTestSuite: grader must not be null");
        }
        this.grader = grader;
    }

    public GuardrailGrader getGrader() {
        return grader;
    }

    /**
     * Run the suite against a guardrail with no transforms (base corpus only).
     */
    public GuardrailTestReport run(IContentGuardrail guardrail, List<AttackPlugin> plugins,
                                   AgentExecutionContext ctx) {
        return run(guardrail, plugins, Collections.emptyList(), ctx);
    }

    /**
     * Run the full pipeline. For each base case from every plugin, the supplied
     * transforms generate additional variants; base cases and variants alike
     * flow through {@code guardrail.check()} → {@code grader.grade()} and are
     * aggregated into a {@link GuardrailTestReport}.
     *
     * @param guardrail  the (real) guardrail under test — must not be null
     * @param plugins    corpus providers; empty list yields an empty report
     *                   (not an error)
     * @param transforms payload transforms; empty list means base corpus only
     * @param ctx        the agent execution context passed to
     *                   {@code guardrail.check()}
     */
    public GuardrailTestReport run(IContentGuardrail guardrail, List<AttackPlugin> plugins,
                                   List<AttackTransform> transforms, AgentExecutionContext ctx) {
        if (guardrail == null) {
            throw new NopAiAgentException("GuardrailTestSuite.run: guardrail must not be null");
        }
        if (plugins == null) {
            throw new NopAiAgentException("GuardrailTestSuite.run: plugins must not be null");
        }
        if (ctx == null) {
            throw new NopAiAgentException("GuardrailTestSuite.run: ctx must not be null");
        }
        List<AttackTransform> tf = transforms == null ? Collections.emptyList() : transforms;

        List<AttackCase> allCases = new ArrayList<>();
        for (AttackPlugin plugin : plugins) {
            if (plugin == null) {
                throw new NopAiAgentException("GuardrailTestSuite.run: plugin list contains null entry");
            }
            for (AttackCase baseCase : plugin.cases()) {
                if (baseCase == null) {
                    throw new NopAiAgentException(
                            "GuardrailTestSuite.run: plugin '" + plugin.name() + "' returned a null case");
                }
                allCases.add(baseCase);
                for (AttackTransform transform : tf) {
                    if (transform == null) {
                        throw new NopAiAgentException(
                                "GuardrailTestSuite.run: transform list contains null entry");
                    }
                    allCases.add(transform.apply(baseCase));
                }
            }
        }

        List<CaseResult> results = new ArrayList<>(allCases.size());
        for (AttackCase ac : allCases) {
            GuardrailResult actual = guardrail.check(ac.getDirection(), ac.getPayload(), ctx);
            if (actual == null) {
                throw new NopAiAgentException(
                        "GuardrailTestSuite.run: guardrail returned null for case " + ac.getId()
                                + " (null results are not allowed — guardrail must return Pass/Block/Modify)");
            }
            GradeResult grade = grader.grade(ac, actual);
            results.add(new CaseResult(ac, actual, grade));
        }
        return GuardrailTestReport.build(results);
    }
}
