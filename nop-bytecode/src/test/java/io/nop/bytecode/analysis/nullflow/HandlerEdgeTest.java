package io.nop.bytecode.analysis.nullflow;

import io.nop.bytecode.TestCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exception-handler edge semantics: a dereference inside a try block whose null-branch exits
 * through an exception must not be reported at the deref site; an unguarded deref inside try
 * must be reported exactly once (handler edges do not duplicate findings — dedup by
 * class/method/insn).
 */
class HandlerEdgeTest {

    private static final String SRC = """
            public class HandlerToy {
                static int guardedInTry(String s) {
                    try {
                        if (s == null) throw new IllegalStateException();
                        return s.length();
                    } catch (IllegalStateException e) {
                        return -1;
                    }
                }
                static int unguardedInTry(String s) {
                    try {
                        return s.length();
                    } catch (NullPointerException e) {
                        return -1;
                    }
                }
            }
            """;

    @Test
    void handlerEdgesDoNotDistortFindings(@TempDir Path tmp) throws Exception {
        Path classes = tmp.resolve("handler-classes");
        TestCompiler.compile("17", classes, "HandlerToy.java", SRC);
        byte[] cls = Files.readAllBytes(classes.resolve("HandlerToy.class"));

        List<DerefFinding> findings = new NullflowAnalyzer().analyze(cls);
        long guarded = findings.stream().filter(f -> f.methodName().equals("guardedInTry")).count();
        long unguarded = findings.stream().filter(f -> f.methodName().equals("unguardedInTry")).count();
        assertEquals(0, guarded, "null path exits via exception; deref edge is NONNULL-only");
        assertEquals(1, unguarded, "unguarded deref reported once despite handler edges");
    }
}
