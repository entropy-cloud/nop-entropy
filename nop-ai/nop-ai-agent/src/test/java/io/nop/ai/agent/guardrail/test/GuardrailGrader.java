package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.guardrail.GuardrailResult;

/**
 * Rubric grader SPI (design {@code guardrail-contract.md} §增量 1, Decision C).
 * The grader produces a deterministic {@link GradeResult} by comparing the
 * guardrail's actual {@link GuardrailResult} against the case's
 * {@link ExpectedBehavior}. See {@link DefaultGuardrailGrader} for the
 * structured judgment matrix. LLM-judge / rubric-template grading is a
 * successor.
 */
public interface GuardrailGrader {

    /**
     * Judge whether the guardrail behaved as expected for this case.
     *
     * @param attackCase the case under test (carries expectedBehavior)
     * @param actual     the guardrail's actual result (must not be null)
     */
    GradeResult grade(AttackCase attackCase, GuardrailResult actual);
}
