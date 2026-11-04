package io.nop.refactor.core.operation;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.NopRefactorException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structured landed payload of a mid-apply failure (plan 2531 W-08,
 * red⑦ — M-P2-7, structural compile red pre-fix): the fail-loud landing
 * discipline's machine-readable face. The human-readable message (reverse
 * protection #8, TestNopRefactorBizModel.midApplyFailureEnumeratesLandedFilesAndAborts)
 * stays the behavioral contract and is not touched; this pin adds the
 * machine-readable {@code landedFiles}/{@code notAttemptedCount} accessors
 * on {@link NopRefactorException} so a caller (GraphQL/CLI consumer) can
 * determine the landed set without parsing message text. The landing
 * semantics itself is fail-loud per file, never batch-atomic; crash
 * recovery is declared as undo-via-VCS in the runner contract.
 */
public class TestRefactorLandedPayload_W_08 {

    private static final String TRANSFORM_PREFIX = "/test/lint/cli-rules-transform";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "operation-landed-payload", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static RewriteRequest request(LanguageRegistry registry,
                                          PreparedTarget... targets) {
        RuleSetLoader.LoadedRuleSet loaded = new RuleSetLoader().loadRuleSet(TRANSFORM_PREFIX);
        return new RewriteRequest(loaded, ExemptionFilter.of(loaded.exemptions()),
                new LintEngine(registry, LintProfile.STANDARD), registry,
                List.of(targets), List.of(), TRANSFORM_PREFIX);
    }

    @Test
    void testW_08_midApplyFailureCarriesMachineReadableLandedPayload() throws Exception {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        Path ok = writeTarget("payload/ok/A.java",
                "class A { void m() { inlineCall(1); } }\n");
        Path blocked = writeTarget("payload/blocked/B.java",
                "class B { void m() { inlineCall(2); } }\n");
        File blockedDir = blocked.getParent().toFile();
        assertTrue(blockedDir.setWritable(false, true),
                "test precondition: the blocked dir must become read-only");
        try {
            NopRefactorException ex = assertThrows(NopRefactorException.class,
                    () -> RefactorOperationRunner.INSTANCE.run(RewriteOperation.INSTANCE,
                            request(registry,
                                    new PreparedTarget(ok, "java", Files.readAllBytes(ok)),
                                    new PreparedTarget(blocked, "java", Files.readAllBytes(blocked))),
                            false));

            // the structured payload (plan 2531 W-08, adjudication 5(b)):
            // the landed set and the not-attempted count are readable
            // without parsing the message text
            assertEquals(List.of(ok.toString()), ex.landedFiles(),
                    "the landed list is machine-readable on the exception");
            assertEquals(1, ex.notAttemptedCount(),
                    "the not-attempted count is machine-readable on the exception");

            // the human-readable contract (reverse protection #8) is intact
            assertTrue(ex.getMessage().contains("already landed"), ex.getMessage());
            assertTrue(ex.getMessage().contains("payload/ok/A.java"), ex.getMessage());

            // landing discipline unchanged: the landed file keeps its
            // rewrite, the blocked file keeps its original content
            assertEquals("class A { void m() { directCall(1); } }\n", Files.readString(ok),
                    "the landed file keeps its rewrite (fail-loud, never a faked success)");
            assertEquals("class B { void m() { inlineCall(2); } }\n", Files.readString(blocked),
                    "the blocked file keeps its original content");
        } finally {
            blockedDir.setWritable(true, true);
        }
    }
}
