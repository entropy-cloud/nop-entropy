package io.nop.git.policy;

/**
 * AI Git operation policy engine (design 03). One engine instance serves both
 * interception points (CLI dispatch and API facade methods); deny always
 * wins; review provides the human confirmation channel.
 */
public interface IGitPolicyEngine {

    /**
     * @return true when at least one policy layer (project or user) exists;
     * an inactive engine intercepts nothing and writes no audit lines.
     */
    boolean isActive();

    /**
     * Execution mode of an active engine.
     */
    GitPolicyMode mode();

    /**
     * Evaluate a normalized operation request against the merged policy.
     */
    GitVerdict evaluate(IGitOperationRequest request);

    /**
     * Resolve a REVIEW verdict: environment approval
     * ({@code NOP_GIT_REVIEW_APPROVED=1}), interactive confirmation, or
     * timeout fallback to deny.
     *
     * @return true when the review was approved
     */
    boolean resolveReview(IGitOperationRequest request, GitVerdict verdict);

    /**
     * Append one JSON Lines audit record with the final outcome. No-op when
     * the engine is inactive.
     */
    void audit(IGitOperationRequest request, GitVerdict verdict, String outcome);
}
