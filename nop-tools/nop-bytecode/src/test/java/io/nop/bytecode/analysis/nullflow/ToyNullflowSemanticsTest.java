package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.TestCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toy semantic lock (POC dialect): the four canonical null-flow cases from the Wave 0 POC.
 * Hit counts are asserted per method, keyed by the toy method names.
 */
class ToyNullflowSemanticsTest {

    private static final String TOY = """
            public class ToyNull {
                static String derefAfterGuardedNullCheck(String s) {
                    if (s == null) { return "null-case"; }
                    return s.trim();
                }
                static String derefWithoutCheck(String s) {
                    return s.trim();
                }
                static String derefAfterRequireNonNull(String s) {
                    return java.util.Objects.requireNonNull(s).trim();
                }
                static String emptyThenStillMayBeNull(String s) {
                    if (s == null) { }
                    return s.trim();
                }
            }
            """;

    @Test
    void toySemanticsLocked(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("toy-classes");
        TestCompiler.compile("17", classes, "ToyNull.java", TOY);
        byte[] toy = Files.readAllBytes(classes.resolve("ToyNull.class"));

        List<DerefFinding> findings = new NullflowAnalyzer().analyze(toy);
        Map<String, Long> hitsByMethod = findings.stream()
                .filter(f -> f.className().equals("ToyNull"))
                .collect(Collectors.groupingBy(DerefFinding::methodName, Collectors.counting()));

        assertEquals(0, hitsByMethod.getOrDefault("derefAfterGuardedNullCheck", 0L),
                "guarded branch: null path exits, deref is NONNULL on the surviving edge");
        assertEquals(1, hitsByMethod.getOrDefault("derefWithoutCheck", 0L),
                "unguarded param deref is a true positive");
        assertEquals(0, hitsByMethod.getOrDefault("derefAfterRequireNonNull", 0L),
                "Objects.requireNonNull is the semantic gate");
        assertEquals(1, hitsByMethod.getOrDefault("emptyThenStillMayBeNull", 0L),
                "empty then-branch: NULL and NONNULL join to MAYNULL -> true positive");
        assertEquals(2, findings.size(), "no other findings expected");
    }
}
