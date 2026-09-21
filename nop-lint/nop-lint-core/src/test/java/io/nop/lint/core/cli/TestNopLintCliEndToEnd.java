package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end and wiring proofs for the {@code nop-lint check} chain
 * (Minimum Rules #22 and #23): argv → option parsing → ServiceLoader
 * language discovery ({@link LanguageRegistry#discoverDefaults()}, the
 * production discovery call) → classpath-VFS rule loading
 * ({@code RuleDslParser} pipeline) → the real {@code LintEngine} per-file
 * pipeline (deadline installation and the suppression tail included) →
 * console report + exit code.
 *
 * <p>The wiring is proven by behavior, not by type presence: a rule hit
 * renders with its declared severity and message; a suppressed twin hit
 * disappears from the diagnostics while {@code suppressed diagnostics}
 * rises in the summary — the suppression tail provably consumed the
 * engine's candidates. Discovery is proven by running through the same
 * registry the production entry point builds.</p>
 */
public class TestNopLintCliEndToEnd {

    private static final String VALID_PREFIX = "/test/lint/cli-rules";

    @TempDir
    Path dir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private Run runThroughProductionDiscovery(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopLintCli.runFull(args, LanguageRegistry.discoverDefaults(), VALID_PREFIX,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exitCode, String stdout, String stderr) {
    }

    @Test
    public void fullChainCleanTreeExitsZero() throws IOException {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n    int ok = 1;\n}\n");

        Run run = runThroughProductionDiscovery("check", "--profile", "standard", dir.toString());

        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stderr());
        assertTrue(run.stderr().isEmpty());
        assertTrue(run.stdout().contains("check complete: scanned=1 files"), run.stdout());
    }

    @Test
    public void fullChainViolationRendersDiagnosticAndExitsOne() throws IOException {
        Files.writeString(dir.resolve("violation.java"),
                """
                        class Violation {
                            void fail() {
                                throw new RuntimeException("boom");
                            }
                        }
                        """);

        Run run = runThroughProductionDiscovery("check", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode());
        assertTrue(run.stdout().contains("violation.java:3: error: demo/no-bare-throw: "
                + "Do not throw bare RuntimeException"), run.stdout());
        assertTrue(run.stdout().contains("rules: loaded=2"), run.stdout());
    }

    @Test
    public void fullChainBadInputExitsTwo() {
        Run run = runThroughProductionDiscovery("check", "/no/such/path/anywhere");

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("nop-lint: error:"), run.stderr());
        assertFalse(run.stdout().contains("check complete"), "no report on internal error");
    }

    @Test
    public void suppressionTailIsWiredIntoTheChain() throws IOException {
        // Minimum Rules #23: the suppressed twin hit must vanish while the
        // suppressed counter rises — the engine's suppression pass really
        // consumed the candidates, and its outcome reaches the console
        Files.writeString(dir.resolve("suppressed.java"),
                """
                        class Suppressed {
                            void log() {
                                // nop-lint-disable-next-line demo/no-print
                                System.out.println("hidden");
                                System.out.println("visible");
                            }
                        }
                        """);

        Run run = runThroughProductionDiscovery("check", dir.toString());

        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stdout() + run.stderr());
        assertTrue(run.stdout().contains("suppressed.java:5: warning: demo/no-print:"),
                "the visible hit must render: " + run.stdout());
        assertFalse(run.stdout().contains("hidden"), "the suppressed hit must not render");
        assertTrue(run.stdout().contains("suppressed diagnostics: 1"), run.stdout());
        assertTrue(run.stdout().contains("diagnostics: error=0, warning=1"), run.stdout());
    }

    @Test
    public void serviceLoaderDiscoveredBindingsAreConsumedAtRuntime() {
        // the production discovery call must yield the bindings the chain
        // needs: the java-id provider (rules declare language: Java) and the
        // stub provider — both loaded through META-INF/services
        LanguageRegistry discovered = LanguageRegistry.discoverDefaults();

        assertTrue(discovered.registeredIds().contains("java"),
                "the java-id test provider must be discovered: " + discovered.registeredIds());
        assertTrue(discovered.registeredIds().contains("stub"),
                "the stub provider must be discovered: " + discovered.registeredIds());
        // and the discovery-built registry drives a real run end to end
        Run run = runThroughProductionDiscovery("check",
                dirWithSingleCleanFile().toString());
        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stderr());
    }

    private Path dirWithSingleCleanFile() {
        try {
            Files.writeString(dir.resolve("discovery.java"), "class Discovery {\n}\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return dir;
    }

    @Test
    public void mixedSeverityTreeAggregatesAllCounts() throws IOException {
        Files.writeString(dir.resolve("warn-only.java"),
                "class W {\n    void x() {\n        System.out.println(\"w\");\n    }\n}\n");
        Files.writeString(dir.resolve("error-only.java"),
                "class E {\n    void x() {\n        throw new RuntimeException(\"e\");\n    }\n}\n");
        Files.writeString(dir.resolve("clean.md"), "skipped bucket");

        Run run = runThroughProductionDiscovery("check", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode());
        assertTrue(run.stdout().contains("check complete: scanned=2 files, skipped=1 (md=1)"),
                run.stdout());
        assertTrue(run.stdout().contains("diagnostics: error=1, warning=1, info=0, hint=0, "
                + "other=0 (total=2)"), run.stdout());
        // file order is stable and lexicographic: error-only.java sorts
        // before warn-only.java
        int errorIndex = run.stdout().indexOf("error-only.java:");
        int warnIndex = run.stdout().indexOf("warn-only.java:");
        assertTrue(errorIndex >= 0 && warnIndex > errorIndex, run.stdout());
    }
}
