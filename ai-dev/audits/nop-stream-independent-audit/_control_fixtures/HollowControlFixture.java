package io.nop.stream.audit.control;

/**
 * POSITIVE-CONTROL FIXTURE for ai-dev/tools/scan-hollow-implementations.mjs.
 *
 * This file is NOT production code. It exists ONLY to prove (positive control) that the
 * scan-hollow tool actually reports high-severity findings when known hollow patterns are
 * injected. Each method below deliberately materializes one of the patterns the scanner
 * is supposed to detect. The Stage-17 governance control runs:
 *
 *   node ai-dev/tools/scan-hollow-implementations.mjs \
 *       ai-dev/audits/nop-stream-independent-audit/_control_fixtures --severity high
 *
 * and asserts the scanner exits NON-ZERO with high/critical findings. If the scanner ever
 * silently exits 0 against this fixture, the tool has regressed (lost its detection power)
 * and must be fixed before any closure gate may rely on a "0 findings" result.
 *
 * Patterns injected (one per method):
 *   - P1: throw new UnsupportedOperationException("not yet implemented")
 *   - P2a: empty method body (no-op)
 *   - P3: empty catch block (swallowed exception)
 *   - P6b: "not yet implemented" comment marker
 *   - P8: bare continue; silent-skip
 */
public class HollowControlFixture {

    public void hollowUnsupportedOperation() {
        // P1 — scanner MUST flag this as high severity
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void hollowEmptyMethodBody() {
        // P2a — scanner MUST flag the empty-body method as high severity
    }

    public void hollowEmptyCatchBlock() {
        try {
            doSomethingRisky();
        } catch (Exception e) {
            // P3 — scanner MUST flag this empty catch block as high severity (swallowed exception)
        }
    }

    public void hollowNotYetImplementedComment() {
        // not yet implemented — scanner MUST flag this comment marker as high severity
    }

    public void hollowBareContinueSkip(java.util.List<String> items) {
        for (String item : items) {
            if (item.startsWith("skip")) {
                continue; // P8 — scanner MUST flag this bare continue as a suspected silent no-op
            }
            doSomethingRisky();
        }
    }

    private void doSomethingRisky() {
        // helper, not a hollow target
    }
}
