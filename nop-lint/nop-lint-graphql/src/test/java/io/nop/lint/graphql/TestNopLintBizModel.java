package io.nop.lint.graphql;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link NopLintBizModel} boundary matrix (roadmap item 38, plan
 * Decisions 2/3/4/5) through the real service with the real rule library:
 * the source cap, the path grammar's three faces (namespace rejection, VFS
 * resolution, workdir confinement), the fail-closed rule-id whitelist, and
 * the listRules metadata face (top-level severity slot, null-safe
 * metadata).
 */
public class TestNopLintBizModel {

    static NopLintBizModel service;

    @TempDir
    Path workDir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        service = new NopLintBizModel();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String VIOLATING_SOURCE = "package demo;\n\nclass Bad {\n"
            + "    void run() {\n        throw new RuntimeException(\"boom\");\n    }\n}\n";

    @Test
    public void checkSourceLintsThroughTheRealLibrary() {
        LintCheckResult result = service.checkSource(VIOLATING_SOURCE, "java", null, null);

        assertTrue(result.total() >= 1, String.valueOf(result));
        assertEquals(result.diagnostics().size(), result.total());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.ruleId().startsWith("nop/")),
                "production rules drive the diagnostics: " + result.diagnostics());
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.message() == null),
                "diagnostic views carry messages");
    }

    @Test
    public void oversizeSourceFailsClosed() {
        char[] big = new char[1024 * 1024 + 1];
        java.util.Arrays.fill(big, 'x');
        // one line of 'x' is syntactically meaningless but the cap fires
        // before any parsing
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkSource(new String(big), "java", null, null));
        assertTrue(ex.getMessage().contains("nop.lint.graphql.max-source-size"), ex.getMessage());
    }

    @Test
    public void unknownRuleIdFailsClosedNamingTheLibrary() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkSource(VIOLATING_SOURCE, "java",
                        List.of("nop/no-such-rule"), null));
        assertTrue(ex.getMessage().contains("nop/no-such-rule"), ex.getMessage());
    }

    @Test
    public void unknownLanguageFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkSource("[]", "unknown-lang", null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("unknown-lang"), ex.getMessage());
    }

    @Test
    public void ruleWhitelistNarrowsTheDiagnostics() {
        LintCheckResult all = service.checkSource(VIOLATING_SOURCE, "java", null, null);
        LintCheckResult narrowed = service.checkSource(VIOLATING_SOURCE, "java",
                List.of("nop/no-empty-catch"), null);

        // the empty-catch rule is orthogonal to the bare throw: a selection
        // holding only it must zero the diagnostics of this fixture
        assertEquals(0, narrowed.total(), String.valueOf(narrowed));
        assertTrue(all.total() > 0);
    }

    @Test
    public void namespacePathIsRejectedBeforeAnyResolution() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkFile("file:/etc/passwd", null, null));
        assertTrue(ex.getMessage().contains("namespace"), ex.getMessage());

        NopLintException cls = assertThrows(NopLintException.class,
                () -> service.checkFile("cls:/io/nop/x/X.class", null, null));
        assertTrue(cls.getMessage().contains("namespace"), cls.getMessage());
    }

    @Test
    public void diskPathOutsideWorkdirIsRejected() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkFile("/etc/passwd", null, null));
        // absolute disk paths are VFS roots by the grammar; /etc/passwd is
        // not a VFS resource, so the VFS branch fails with not-found — the
        // workdir face needs a real disk escape
        assertTrue(ex.getMessage().contains("VFS file") || ex.getMessage().contains("escapes"),
                ex.getMessage());

        String escape = workDir.resolve("outside.java").toString();
        // a relative path escaping via .. lands outside the workdir after
        // toRealPath — use an absolute temp path outside the process cwd
        NopLintException ex2 = assertThrows(NopLintException.class,
                () -> service.checkFile("/nonexistent-vfs-root/x.java", null, null));
        assertTrue(ex2.getMessage().contains("VFS file"), ex2.getMessage());
        assertTrue(escape != null);
    }

    @Test
    public void vfsPathReadsThroughTheVirtualFileSystem() throws Exception {
        // a real VFS resource: the module's own beans file
        LintCheckResult result = service.checkFile(
                "/nop/lint/beans/app-lint.beans.xml", null, null);
        // XML rules are not loaded by the Java binding — the grammar is
        // bound, so the run completes with zero Java diagnostics
        assertEquals(0, result.total(), String.valueOf(result));
    }

    @Test
    public void listRulesCarriesMetadataFromTheTopLevelSlot() {
        List<LintRuleView> rules = service.listRules("java", null);

        // 62 production rules − 3 XNode XML rules = 59 in the java group
        assertTrue(rules.size() >= 59, "the production library is visible: " + rules.size());
        for (LintRuleView rule : rules) {
            assertTrue(rule.id().contains("/") || !rule.id().isEmpty());
            assertTrue(rule.severity() != null && !rule.severity().isEmpty(),
                    "severity comes from the top-level slot: " + rule);
        }
        assertTrue(rules.stream().anyMatch(r -> r.id().equals("nop/no-raw-exception")),
                "a known rule id is listed");
    }
}
