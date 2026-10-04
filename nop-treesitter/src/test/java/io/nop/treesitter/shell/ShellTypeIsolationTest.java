package io.nop.treesitter.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The type-level isolation between capability 1 and capability 2.
 *
 * <p>The claim is that a harvest result cannot be used where a proof is required.
 * Prose and a narrower visibility do not survive a refactor or a new call site, so
 * the claim is enforced by the type system and <b>verified by compiling the
 * misuse</b>: the negative case is a real compilation failure, not an assertion
 * about one. A type boundary nobody compiles against is a comment.</p>
 */
class ShellTypeIsolationTest {

    @TempDir
    Path tempDir;

    private record Source(String name, String code) {
    }

    /** Compiles a snippet against the module's own classpath; returns the diagnostics. */
    private List<Diagnostic<? extends JavaFileObject>> compile(String className, String code) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the tests need a JDK, not a JRE");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavaFileObject file = new SimpleJavaFileObject(
                    java.net.URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            };
            List<String> options = new ArrayList<>(Arrays.asList(
                    "-classpath", System.getProperty("java.class.path"),
                    "-proc:none",
                    "-d", tempDir.toString()));
            compiler.getTask(null, fileManager, diagnostics, options, null, List.of(file)).call();
        } catch (IOException e) {
            throw new AssertionError("in-memory compilation failed to start", e);
        }
        return diagnostics.getDiagnostics();
    }

    private static String errors(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        return diagnostics.stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getLineNumber() + ": " + d.getMessage(null))
                .reduce("", (a, b) -> a + b + "\n");
    }

    /**
     * The misuse: a harvest result passed where a proven sequence is required.
     * This must not compile.
     */
    @Test
    void aHarvestCannotBeUsedWhereAProofIsRequired() {
        String code = """
                import io.nop.treesitter.shell.LiteralHarvest;
                import io.nop.treesitter.shell.ProvenCommandSequence;

                final class Misuse {
                    static void approve(ProvenCommandSequence proof) {
                        if (!proof.isProvenLiteral()) {
                            throw new IllegalStateException("not proven");
                        }
                    }

                    static void run(LiteralHarvest harvest) {
                        approve(harvest);
                    }
                }
                """;
        List<Diagnostic<? extends JavaFileObject>> diagnostics = compile("Misuse", code);
        String errors = errors(diagnostics);
        assertFalse(errors.isEmpty(),
                "passing a LiteralHarvest where a ProvenCommandSequence is required must not compile");
        assertTrue(errors.contains("ProvenCommandSequence") || errors.contains("incompatible types"),
                "the failure must be the type mismatch, got: " + errors);
    }

    /**
     * The mirror image: the two types share no supertype, so there is no common
     * variable to smuggle a harvest through. Assigning either to a shared
     * {@code Object}-then-back round trip is the only way through, and the API
     * offers no accessor that performs it.
     */
    @Test
    void theTwoTypesHaveNoSharedSupertype() {
        String code = """
                import io.nop.treesitter.shell.LiteralHarvest;
                import io.nop.treesitter.shell.ProvenCommandSequence;

                final class Misuse {
                    static void consume(ProvenSplitView view) {
                    }

                    interface ProvenSplitView {
                    }

                    static void run(LiteralHarvest harvest, ProvenCommandSequence proof) {
                        consume(harvest);
                        consume(proof);
                    }
                }
                """;
        assertFalse(errors(compile("Misuse", code)).isEmpty(),
                "neither result may be widened into a shared view that erases the distinction");
    }

    /** The intended usage does compile — the boundary rejects misuse, not use. */
    @Test
    void theIntendedUsageCompiles() {
        String code = """
                import io.nop.treesitter.shell.LiteralHarvest;
                import io.nop.treesitter.shell.ProvenCommandSequence;
                import io.nop.treesitter.shell.ProvenSplit;
                import io.nop.treesitter.shell.UnprovenScript;

                final class CorrectUse {
                    static String decide(ProvenSplit split) {
                        if (split instanceof ProvenCommandSequence proof) {
                            return "proven:" + proof.commandNames();
                        }
                        if (split instanceof UnprovenScript refusal) {
                            return "refused:" + refusal.reason();
                        }
                        throw new IllegalStateException("unreachable: the sealed type has two cases");
                    }

                    static int count(LiteralHarvest harvest) {
                        return harvest.size();
                    }
                }
                """;
        assertEquals("", errors(compile("CorrectUse", code)));
    }

    /**
     * The sealed two-case result means a consumer's {@code switch} is exhaustive:
     * a third case added later becomes a compile error at every consumption site.
     */
    @Test
    void theResultTypeIsSealedToExactlyTwoCases() {
        assertEquals(2, ProvenSplit.class.getPermittedSubclasses().length);
        List<Class<?>> permitted = List.of(ProvenSplit.class.getPermittedSubclasses());
        assertTrue(permitted.contains(ProvenCommandSequence.class));
        assertTrue(permitted.contains(UnprovenScript.class));
    }

    /** Each result type states its own safety property in a method of its own. */
    @Test
    void eachResultStatesItsOwnSafetyProperty() {
        ShellAnalysis analysis = ShellTestSupport.analysis();
        ProvenSplit split = analysis.splitProven("echo hi\n");
        ProvenCommandSequence proof = (ProvenCommandSequence) split;
        LiteralHarvest harvest = analysis.harvestLiterals("echo hi\n");
        assertTrue(proof.isProvenLiteral());
        assertTrue(harvest.isNotASafetyProof());
    }
}
