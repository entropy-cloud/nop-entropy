package io.nop.lint.graphql;

import io.nop.api.core.config.AppConfig;
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
    public void vfsBranchRejectsUnresolvableRoot() {
        // absolute paths are VFS roots by the grammar; an unregistered root
        // fails with the not-found face, never a silent empty result
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkFile("/nonexistent-vfs-root/x.java", null, null));
        assertTrue(ex.getMessage().contains("VFS file"), ex.getMessage());
    }

    @Test
    public void diskPathInsideWorkdirIsReadable() throws Exception {
        // grammar branch (c) positive face: a disk file inside the process
        // working directory resolves and lints (target/ is inside cwd)
        Path inside = Path.of("target", "nop-lint-graphql-disk-face.java");
        Files.createDirectories(inside.getParent());
        Files.writeString(inside, "class Clean {\n}\n");

        LintCheckResult result = null;
        try {
            result = service.checkFile(inside.toString(), List.of("nop/no-raw-exception"), null);
        } finally {
            Files.deleteIfExists(inside);
        }
        assertEquals(0, result.total(), String.valueOf(result));
    }

    @Test
    public void relativePathEscapingWorkdirIsRejected() {
        // grammar branch (c) escape face: '..' traversal lands outside the
        // working directory after toRealPath and must be rejected — the
        // workdir-confinement defense that guards against arbitrary file
        // read. '../pom.xml' EXISTS outside the module directory, so the
        // containment check itself (not just a missing-file error) is what
        // rejects the read
        NopLintException ex = assertThrows(NopLintException.class,
                () -> service.checkFile("../pom.xml", null, null));
        assertTrue(ex.getMessage().contains("escapes the working directory"), ex.getMessage());
    }

    @Test
    public void checkFileContentAboveCapFailsClosed() throws Exception {
        // the source cap applies to checkFile's read content too (plan
        // Decision 5): a >1MB file inside the working directory is rejected
        // before parsing
        Path big = Path.of("target", "nop-lint-graphql-big.java");
        Files.createDirectories(big.getParent());
        char[] chunk = new char[1024];
        java.util.Arrays.fill(chunk, 'x');
        try (var writer = Files.newBufferedWriter(big)) {
            for (int i = 0; i < 1024 + 1; i++) {
                writer.write(chunk);
                writer.write("\n");
            }
        }
        try {
            NopLintException ex = assertThrows(NopLintException.class,
                    () -> service.checkFile(big.toString(), null, null));
            assertTrue(ex.getMessage().contains("nop.lint.graphql.max-source-size"),
                    ex.getMessage());
        } finally {
            Files.deleteIfExists(big);
        }
    }

    @Test
    public void checkFileOversizedDiskFileIsRejectedBeforeRead() throws Exception {
        // the cap gates the READ (plan 07, audit finding C6): the disk
        // branch measures the file and rejects it before its content
        // occupies memory — the message names the pre-read face
        Path big = Path.of("target", "nop-lint-graphql-big-preread.java");
        Files.createDirectories(big.getParent());
        byte[] megabyte = new byte[1024 * 1024];
        java.util.Arrays.fill(megabyte, (byte) 'x');
        try (var out = Files.newOutputStream(big)) {
            out.write(megabyte);
            out.write(megabyte);
        }
        try {
            NopLintException ex = assertThrows(NopLintException.class,
                    () -> service.checkFile(big.toString(), null, null));
            assertTrue(ex.getMessage().contains("rejected before read"), ex.getMessage());
            assertTrue(ex.getMessage().contains("bytes"), ex.getMessage());
        } finally {
            Files.deleteIfExists(big);
        }
    }

    @Test
    public void checkFileResolvesUppercaseExtension() throws Exception {
        // the extension normalizes case before the TargetScanner table
        // lookup (plan 07, audit R1 Major 2): a FILE.JAVA name must lint
        // through the java binding instead of dying in resolve(null) — the
        // fixture lives inside the working directory like the disk-branch
        // tests above
        Path file = Path.of("target", "nop-lint-graphql-uppercase-demo.JAVA");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Clean {\n}\n");
        try {
            LintCheckResult result = service.checkFile(file.toString(),
                    List.of("nop/no-raw-exception"), null);
            assertEquals(0, result.total(), String.valueOf(result));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void fastProfileIsPinnedDeepRulesNeverFire() {
        // the service builds its engine over LintProfile.FAST: a deep-only
        // rule (requires L3) may be selected explicitly but its diagnostics
        // are skipped by profile, not produced through a faked analysis
        String deepTrigger = "package demo;\n\nclass D {\n    void run() {\n"
                + "        int unused = compute();\n    }\n"
                + "    int compute() {\n        return 1;\n    }\n}\n";
        LintCheckResult result = service.checkSource(deepTrigger, "java",
                List.of("quality/unused-local-variable"), null);
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.ruleId()
                .equals("quality/unused-local-variable")),
                "the L3 rule must not fire under the pinned fast profile: " + result);
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
    public void vfsBranchOversizedResourceIsRejectedBeforeRead() {
        // the VFS pre-read gate (plan 07 closure-audit R2): a small cap makes
        // any real VFS resource oversized; the rejection must name the
        // pre-read face — the content is never read into memory
        Integer originalCap = NopLintBizModel.CFG_MAX_SOURCE_SIZE.get();
        try {
            AppConfig.getConfigProvider().updateConfigValue(
                    NopLintBizModel.CFG_MAX_SOURCE_SIZE, 10);

            NopLintException ex = assertThrows(NopLintException.class,
                    () -> service.checkFile("/nop/lint/beans/app-lint.beans.xml", null, null));
            assertTrue(ex.getMessage().contains("rejected before read"), ex.getMessage());
            assertTrue(ex.getMessage().contains("nop.lint.graphql.max-source-size"),
                    ex.getMessage());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(
                    NopLintBizModel.CFG_MAX_SOURCE_SIZE, originalCap);
        }
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
