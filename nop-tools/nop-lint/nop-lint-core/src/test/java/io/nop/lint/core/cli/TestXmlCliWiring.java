package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
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
 * CLI wiring proof for the XML binding (plan Phase 2, Minimum Rules #23):
 * a {@code .xml} target travels the real chain {@code TargetScanner →
 * CheckRunner → LintEngine} — the registry is populated by ServiceLoader
 * discovery (no stubs), the rule loads through the classpath VFS, and the
 * diagnostic proves the XNode matcher was actually consumed at runtime.
 * The {@code xml→xml} extension entry shares the scanner's table with the
 * RuleTester's fixture-suffix derivation, so both directions stay in step.
 */
public class TestXmlCliWiring {

    private static final String RULES_PREFIX = "/test/lint/cli-rules-xml";

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
    public void xmlTargetsAreLintedThroughTheDiscoveredBinding() throws IOException {
        Files.writeString(dir.resolve("persistence.xml"), """
                <config>
                    <pool>
                        <driver>com.mysql.jdbc.Driver</driver>
                    </pool>
                </config>
                """);
        Files.writeString(dir.resolve("clean.xml"), """
                <config>
                    <pool>
                        <driver>org.postgresql.Driver</driver>
                    </pool>
                </config>
                """);
        Files.writeString(dir.resolve("notes.txt"), "not linted");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        assertTrue(registry.registeredIds().contains("xml"),
                "the ServiceLoader discovery must surface the XML binding: " + registry.registeredIds());

        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        assertEquals(2, scan.lintable().size(), ".xml files must classify as lintable");
        assertEquals("xml", scan.lintable().get(0).languageId());
        assertTrue(scan.lintable().get(0).path().getFileName().toString().endsWith("clean.xml"));
        assertEquals("xml", scan.lintable().get(1).languageId());
        assertTrue(scan.lintable().get(1).path().getFileName().toString().endsWith("persistence.xml"));
        assertEquals(1, scan.skipped().total(), "txt is an explicit skip bucket");
        assertEquals(1, scan.skipped().byExtension().get("txt"));

        CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD);

        assertEquals(2, outcome.summary().getFilesScanned());
        assertEquals(1, outcome.summary().getRulesLoaded());
        assertEquals(2, outcome.summary().getRulesExecuted(),
                "both files reach the rule's matcher (no kind filter hit: the tag space is shared)");
        assertEquals(1, outcome.summary().getTotalDiagnostics(),
                "one driver hit, through the real XNode parse");

        FileFindings dirty = outcome.findings().get(1);
        assertTrue(dirty.displayPath().endsWith("persistence.xml"));
        assertEquals(1, dirty.diagnostics().size());
        assertEquals("demo/xml-no-legacy-driver", dirty.diagnostics().get(0).ruleId());
        assertEquals(3, new io.nop.lint.core.node.LineIndex(
                        Files.readString(Path.of(dirty.displayPath())))
                .startLine(dirty.diagnostics().get(0).range()));

        FileFindings clean = outcome.findings().get(0);
        assertTrue(clean.displayPath().endsWith("clean.xml"));
        assertEquals(0, clean.diagnostics().size(), "the valid fixture emits nothing");
        assertFalse(outcome.hasErrorDiagnostics(), "warning-severity findings are not errors");
    }

    @Test
    public void extensionTableStaysSymmetricForXml() {
        // The table is keyed by lowercased extensions (the scanner lowercases
        // before lookup); both query directions answer in normalized form.
        // The platform's XML-family models (.orm.xml via .xml, .xbiz) share
        // the one binding.
        assertEquals("xml", TargetScanner.languageIdForExtension("xml"));
        assertEquals("xml", TargetScanner.languageIdForExtension("xbiz"));
        assertEquals(List.of("xbiz", "xml"), TargetScanner.extensionsForLanguage("xml"));
        assertEquals(List.of("xbiz", "xml"), TargetScanner.extensionsForLanguage("XML"));
    }
}
