// Fixture: intentionally violating samples for check-nop-stream-invariants.mjs self-test
// (scan-output-contract, invariant #6 / PD-15 output-contract family).
// This file is NOT part of the nop-stream source tree (lives under ai-dev fixtures);
// it exists so the scanner's self-test can prove "能红" against deliberate violations:
//   1. EmptyBodyOutput implements Output but its collect(OutputTag, ...) body is empty
//      (classified `forward` in the self-test registry) -> V3 behavior drift must be red.
//   2. EmitterOperator emits through `output.collect(outputTag, ...)` with a parameter-form
//      OutputTag declaration -> an unregistered emission point must be red (V4).
// NOT scanned by the normal `all` / `scan-output-contract` run (scan scope = nop-stream
// modules' src/main/java only).

package fixtures;

public class OutputContractFixture {

    public static class EmptyBodyOutput implements Output<StreamRecord<Object>> {
        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            // VIOLATION (V3): empty body with registry classification forward -> behavior drift
        }
    }

    public static class EmitterOperator {
        private Output<StreamRecord<Object>> output;

        // VIOLATION (V4): unregistered emission point (parameter-form OutputTag declaration)
        public <X> void emit(OutputTag<X> outputTag, X value) {
            output.collect(outputTag, new StreamRecord<>(value));
        }
    }
}
