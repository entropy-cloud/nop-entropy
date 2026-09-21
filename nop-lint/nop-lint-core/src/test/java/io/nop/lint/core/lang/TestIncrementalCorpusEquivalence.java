package io.nop.lint.core.lang;

import io.nop.lint.core.testing.TreeEquivalenceOracle;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.incremental.TSInputEdit;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness gate for the whole incremental chain (plan Phase 3): over real
 * Java corpora and an edit matrix plus a seeded random-edit loop (fixed seed,
 * fully reproducible), the incremental product must equal the full reparse
 * under the {@link TreeEquivalenceOracle}. Every instance names its corpus
 * and seed-derived index so a red run is replayable by seed alone.
 */
public class TestIncrementalCorpusEquivalence {

    /**
     * Fixed seed: the random sequence (corpus choice, edit kinds, positions,
     * payload bytes, resampling attempts) is a pure function of this value and
     * the instance index.
     */
    private static final long SEED = 20260922L;

    /**
     * 6 corpora × 20 random instances = 120, plus the 11 deterministic matrix
     * instances — comfortably above the ≥100 gate; asserted at runtime so the
     * bound cannot rot silently.
     */
    private static final int RANDOM_INSTANCES_PER_CORPUS = 20;

    /**
     * Random byte splices can leave the Java grammar's error-recovery domain
     * entirely — the full parse itself then throws before any incremental
     * comparison exists. Such instances are resampled (deterministically, by
     * attempt index) instead of counted; the bound keeps the resampling from
     * hiding an incremental-only failure, which still propagates as a real
     * defect.
     */
    private static final int MAX_ATTEMPTS = 64;

    private static final Language JAVA =
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");

    // ==================== corpora: real in-repo Java sources, fail-closed ====================

    private static List<byte[]> loadCorpora() throws Exception {
        List<Path> paths = List.of(
                Paths.get("../bench/corpus/OrderService.java"),
                Paths.get("../nop-lint-nop/src/test/resources/_vfs/test/lint/suites/"
                        + "exception/no-empty-catch/invalid/boundary.java"),
                Paths.get("../nop-lint-nop/src/test/resources/_vfs/test/lint/suites/"
                        + "api/bizmodel-safe-api/invalid/basic.java"),
                Paths.get("../nop-lint-nop/src/test/resources/_vfs/test/lint/suites/"
                        + "api/ibiz-missing-annotation/invalid/basic.java"),
                Paths.get("../nop-lint-nop/src/test/resources/_vfs/test/lint/suites/"
                        + "api/ibiz-missing-context/invalid/basic.java"),
                Paths.get("../nop-lint-nop/src/test/resources/_vfs/test/lint/suites/"
                        + "exception/no-raw-exception/invalid/basic.java"));
        List<byte[]> corpora = new ArrayList<>();
        for (Path path : paths) {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("corpus file missing: " + path.toAbsolutePath());
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                throw new IllegalStateException("corpus file empty: " + path.toAbsolutePath());
            }
            corpora.add(bytes);
        }
        return corpora;
    }

    // ==================== the gate itself ====================

    @Test
    public void incrementalEqualsFullParseAcrossCorpusEditMatrixAndRandomLoop() throws Exception {
        List<byte[]> corpora = loadCorpora();
        int instances = 0;

        for (int c = 0; c < corpora.size(); c++) {
            instances += runRandomInstances("corpus#" + c, corpora.get(c));
        }
        instances += runMatrixInstances();

        assertTrue(instances >= 100,
                "the gate must run at least 100 instances, ran " + instances);
        System.out.println("TestIncrementalCorpusEquivalence: " + instances
                + " instances passed (seed " + SEED + ")");
    }

    private int runRandomInstances(String corpusName, byte[] corpus) {
        int passed = 0;
        for (int i = 0; i < RANDOM_INSTANCES_PER_CORPUS; i++) {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                long instanceSeed = SEED + i * 1_000_003L + corpusName.hashCode()
                        + attempt * 7919L;
                Random rnd = new Random(instanceSeed);
                byte[] edited = randomlyEdit(corpus, rnd);
                String instance = corpusName + " instance " + i
                        + (attempt == 0 ? "" : " (resample " + attempt + ")")
                        + " (seed " + instanceSeed + ")";
                if (assertGate(corpus, edited, instance)) {
                    passed++;
                    break;
                }
                assertTrue(attempt + 1 < MAX_ATTEMPTS,
                        "no in-domain edit found for " + instance);
            }
        }
        return passed;
    }

    private int runMatrixInstances() {
        byte[][] matrix = {
                bytes("int a = 1;\n"),
                bytes(""),
                bytes("int a = 1;\n"),
                bytes("class A { void m() {} }\n"),
                bytes("class A { void m(int x) {} }\n"),
                bytes("class A {}\n"),
                bytes("a\nb\nc\nd\ne\n"),
                bytes("a\nX\nc\nd\nY\n"),
                bytes("first\r\nsecond\r\nthird\r\n"),
                bytes("first\r\nSECOND\r\nthird\r\n"),
                bytes("String s = \"中文\"; // éà\n"),
                bytes("String s = \"中文!\"; // éà\n")
        };
        int passed = 0;
        for (int i = 0; i + 1 < matrix.length; i += 2) {
            assertGate(matrix[i], matrix[i + 1], "matrix pair " + (i / 2));
            passed++;
        }
        return passed;
    }

    /**
     * The gate for one instance: diff → parseIncremental → oracle equivalence
     * against a full reparse of the edited source. Returns false when the
     * edited source itself is outside the grammar's recoverable domain (the
     * full parse fails, so no oracle target exists) — the caller resamples.
     */
    private static boolean assertGate(byte[] oldSource, byte[] newSource, String instance) {
        TSTree oldTree = TSParser.parse(JAVA, oldSource);
        TSTree full;
        try {
            full = TSParser.parse(JAVA, newSource);
        } catch (TreeSitterException e) {
            return false;
        }
        List<TSInputEdit> edits = EditCalculator.diff(oldSource, newSource);
        TSTree incremental = TSParser.parseIncremental(JAVA, oldTree, edits, newSource);
        TreeEquivalenceOracle.assertEquivalent(full, incremental, "incremental ≡ full: " + instance);
        assertEquals(newSource.length, incremental.rootNode().endByte(),
                "root must span the edited source: " + instance);
        return true;
    }

    // ==================== seeded random edit generator ====================

    private static byte[] randomlyEdit(byte[] source, Random rnd) {
        byte[] current = source;
        int editCount = 1 + rnd.nextInt(4);
        for (int i = 0; i < editCount; i++) {
            int kind = rnd.nextInt(3);
            if (kind != 0 && current.length == 0) {
                kind = 0; // nothing to delete or replace in an empty source
            }
            int pos = rnd.nextInt(current.length + 1);
            switch (kind) {
                case 0 -> current = splice(current, pos, 0, randomPayload(rnd));
                case 1 -> {
                    int removal = Math.min(24, current.length - pos);
                    if (removal > 0) {
                        current = splice(current, pos, removal, new byte[0]);
                    }
                }
                default -> {
                    int removal = Math.min(16, current.length - pos);
                    current = removal == 0
                            ? splice(current, pos, 0, randomPayload(rnd))
                            : splice(current, pos, removal, randomPayload(rnd));
                }
            }
        }
        return current;
    }

    private static byte[] randomPayload(Random rnd) {
        int len = 1 + rnd.nextInt(20);
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = rnd.nextInt(10) == 0 ? (byte) '\n' : (byte) (33 + rnd.nextInt(90));
        }
        return out;
    }

    private static byte[] splice(byte[] src, int pos, int removeLen, byte[] inserted) {
        byte[] out = new byte[src.length - removeLen + inserted.length];
        System.arraycopy(src, 0, out, 0, pos);
        System.arraycopy(inserted, 0, out, pos, inserted.length);
        System.arraycopy(src, pos + removeLen, out, pos + inserted.length,
                src.length - pos - removeLen);
        return out;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
