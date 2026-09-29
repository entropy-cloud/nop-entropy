package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.bench.CorpusGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-corpus analysis: every synthetic class is analyzed without exceptions, the hit list is
 * non-empty, and two runs produce identical results (determinism anchor).
 */
class CorpusAnalysisTest {

    @Test
    void corpusAnalyzesDeterministicallyWithHits() {
        NullflowAnalyzer analyzer = new NullflowAnalyzer();
        List<byte[]> corpus = CorpusGenerator.generate();
        List<DerefFinding> run1 = analyzer.analyze(corpus.get(0));
        List<DerefFinding> run2 = analyzer.analyze(corpus.get(0));
        assertEquals(run1, run2, "two runs over the same input must be identical");

        int total = 0;
        for (byte[] classBytes : corpus) {
            total += analyzer.analyze(classBytes).size();
        }
        assertTrue(total > 0, "synthetic corpus contains unguarded derefs; hits expected");
    }
}
