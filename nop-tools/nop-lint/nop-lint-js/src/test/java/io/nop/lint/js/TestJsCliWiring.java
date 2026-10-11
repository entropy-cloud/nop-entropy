package io.nop.lint.js;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.cli.CheckOutcome;
import io.nop.lint.core.cli.CheckRunner;
import io.nop.lint.core.cli.FileFindings;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI wiring proof for the TypeScript/TSX bindings (plan item 19 Phase 2,
 * Minimum Rules #23): {@code .ts} and {@code .tsx} fixtures travel the real
 * chain {@code TargetScanner → CheckRunner → LintEngine} — the registry is
 * populated by ServiceLoader discovery (no stubs), rules load through the
 * classpath VFS, and the diagnostics assert the bindings were actually
 * consumed at runtime, not merely registered. The unmatched-extension
 * buckets (including {@code .mts}, deliberately outside the v1 table) stay
 * explicit.
 */
public class TestJsCliWiring {

    private static final String RULES_PREFIX = "/test/lint/cli-rules";

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

    @Test
    public void tsAndTsxTargetsAreLintedThroughTheDiscoveredBindings() throws IOException {
        Files.writeString(dir.resolve("dirty.ts"),
                """
                        function boot(): void {
                            console.log("booting");
                        }
                        boot();
                        """);
        Files.writeString(dir.resolve("page.tsx"),
                """
                        export function Page(): JSX.Element {
                            console.log("rendering");
                            return <div>hi</div>;
                        }
                        """);
        Files.writeString(dir.resolve("notes.md"), "not linted");
        Files.writeString(dir.resolve("module.mts"), "export const x = 1;\n");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        assertTrue(registry.registeredIds().contains("typescript")
                        && registry.registeredIds().contains("tsx"),
                "the ServiceLoader discovery must surface both bindings: "
                        + registry.registeredIds());

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        assertEquals(2, scan.lintable().size(), ".ts and .tsx must classify as lintable");
        assertEquals("typescript", scan.lintable().get(0).languageId());
        assertTrue(scan.lintable().get(0).path().getFileName().toString().endsWith("dirty.ts"));
        assertEquals("tsx", scan.lintable().get(1).languageId());
        assertTrue(scan.lintable().get(1).path().getFileName().toString().endsWith("page.tsx"));
        assertEquals(2, scan.skipped().total(), "md and mts are explicit skip buckets");
        assertEquals(1, scan.skipped().byExtension().get("md"));
        assertEquals(1, scan.skipped().byExtension().get("mts"));

        CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD);

        assertEquals(2, outcome.summary().getFilesScanned());
        assertEquals(1, outcome.summary().getRulesLoaded(),
                "each per-file run loads its language's own rule group (max over runs)");
        assertTrue(outcome.summary().getRulesExecuted() >= 2,
                "both files must reach their rule's matcher");
        assertEquals(2, outcome.summary().getTotalDiagnostics(),
                "one console.log hit per file, through the real binding parses");
        assertFalse(outcome.hasErrorDiagnostics());

        FileFindings tsFindings = outcome.findings().get(0);
        assertTrue(tsFindings.displayPath().endsWith("dirty.ts"));
        assertEquals(1, tsFindings.diagnostics().size());
        assertEquals("demo/ts-no-console", tsFindings.diagnostics().get(0).ruleId(),
                "the TypeScript-declared rule must drive the .ts target");

        FileFindings tsxFindings = outcome.findings().get(1);
        assertTrue(tsxFindings.displayPath().endsWith("page.tsx"));
        assertEquals(1, tsxFindings.diagnostics().size());
        assertEquals("demo/tsx-no-console", tsxFindings.diagnostics().get(0).ruleId(),
                "the TSX-declared rule must drive the .tsx target");
    }
}
