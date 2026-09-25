package io.nop.refactor.graphql;

import io.nop.api.core.config.AppConfig;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The service-level boundary matrix of the GraphQL rewrite face
 * (nop-refactor WI6 Phase 1): preview computes and does not write, apply
 * lands, the load gate and the write-face path grammar fail closed, the
 * resource caps reject before read, and exempted / unsupported / no-rules
 * targets surface as structured nonApplied entries. Targets live under the
 * module dir ({@code target/...}) — the write-face path grammar constrains
 * rewrites to the working directory.
 */
public class TestNopRefactorBizModel {

    private static final String RULES_PREFIX = "/test/lint/graphql-rewrite";

    private final NopRefactorBizModel bizModel = new NopRefactorBizModel();

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private RewriteInput input(String mode, String... paths) {
        RewriteInput input = new RewriteInput();
        input.setRulesetPrefix(RULES_PREFIX);
        input.setPaths(List.of(paths));
        return input;
    }

    private Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "refactor-graphql-test", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    public void previewComputesTheDiffAndWritesNothing() throws Exception {
        Path file = writeTarget("src/Ok.java",
                "class Ok { void m() { inlineCall(1); } }\n");
        RefactorResult result = bizModel.previewRewrite(
                input("preview", "target/refactor-graphql-test/src/Ok.java"));

        assertFalse(result.applied());
        assertFalse(result.diff().isEmpty(), "the diff face carries the proposal");
        assertTrue(result.diff().contains("directCall"), result.diff());
        assertEquals("class Ok { void m() { inlineCall(1); } }\n", Files.readString(file),
                "preview never touches the file");
        assertTrue(result.verification().parseOk());
    }

    @Test
    public void applyLandsTheRewriteAndReportsApplied() throws Exception {
        Path file = writeTarget("apply/A.java",
                "class A { void m() { inlineCall(1); } }\n");
        RefactorResult result = bizModel.applyRewrite(
                input("apply", "target/refactor-graphql-test/apply/A.java"));

        assertTrue(result.applied());
        assertEquals("class A { void m() { directCall(1); } }\n", Files.readString(file));
    }

    @Test
    public void applyRecomputesDeterministically() throws Exception {
        // stateless re-execution: preview and apply produce the same diff for
        // the same input (baseline §三 — no plan token, no server state)
        String content = "class D { void m() { inlineCall(1); } }\n";
        Path previewFile = writeTarget("determinism/P.java", content);
        Path applyFile = writeTarget("determinism/A.java", content);

        RefactorResult preview = bizModel.previewRewrite(
                input("preview", "target/refactor-graphql-test/determinism/P.java"));
        RefactorResult apply = bizModel.applyRewrite(
                input("apply", "target/refactor-graphql-test/determinism/A.java"));

        // the operation is deterministic: same input, same plan — the diff
        // headers carry the (differently named) target paths, so the body
        // lines are what the equality compares
        assertEquals(bodyLines(preview.diff()), bodyLines(apply.diff()),
                "the operation is deterministic: same input, same plan");
    }

    private static List<String> bodyLines(String diff) {
        return diff.lines().filter(line -> !line.startsWith("---") && !line.startsWith("+++"))
                .toList();
    }

    @Test
    public void exemptedRuleProducesStructuredOutOfScope() throws Exception {
        Path file = writeTarget("legacy/L.java",
                "class L { void m() { System.out.println(\"x\"); } }\n");
        RefactorResult result = bizModel.applyRewrite(
                input("apply", "target/refactor-graphql-test/legacy/L.java"));

        boolean exempted = result.nonApplied().stream()
                .anyMatch(n -> n.reason() == NonApply.Reason.OUT_OF_SCOPE
                        && n.detail().contains("exempted"));
        assertTrue(exempted, "the exemption surfaces as a structured entry: "
                + result.nonApplied());
        assertTrue(Files.readString(file).contains("System.out.println"),
                "the exempted rewrite did not land");
    }

    @Test
    public void unsupportedExtensionIsStructuredOutOfScope() throws Exception {
        Path file = writeTarget("src/notes.md", "# notes\n");
        RefactorResult result = bizModel.previewRewrite(
                input("preview", "target/refactor-graphql-test/src/notes.md"));

        boolean unsupported = result.nonApplied().stream()
                .anyMatch(n -> n.detail().contains("unsupported extensions"));
        assertTrue(unsupported, "the skip is structured, never silent: "
                + result.nonApplied());
    }

    @Test
    public void namespacePrefixIsRejected() {
        RewriteInput input = input("apply", "nop:/test/x.java");
        NopRefactorException ex = assertThrows(NopRefactorException.class,
                () -> bizModel.applyRewrite(input));
        assertTrue(ex.getMessage().contains("namespace"), ex.getMessage());
    }

    @Test
    public void rootedPathIsRejectedOnTheWriteFace() {
        RewriteInput input = input("apply", "/etc/hostname");
        NopRefactorException ex = assertThrows(NopRefactorException.class,
                () -> bizModel.applyRewrite(input));
        assertTrue(ex.getMessage().contains("'-rooted"), ex.getMessage());
    }

    @Test
    public void workdirEscapeIsRejected() {
        RewriteInput input = input("apply", "../outside/X.java");
        NopRefactorException ex = assertThrows(NopRefactorException.class,
                () -> bizModel.applyRewrite(input));
        assertTrue(ex.getMessage().contains("working directory"), ex.getMessage());
    }

    @Test
    public void missingRulesetPrefixFailsClosed() {
        RewriteInput input = new RewriteInput();
        input.setPaths(List.of("target"));
        NopRefactorException ex = assertThrows(NopRefactorException.class,
                () -> bizModel.applyRewrite(input));
        assertTrue(ex.getMessage().contains("rulesetPrefix"), ex.getMessage());
    }

    @Test
    public void unknownRulesetPrefixFailsClosed() {
        RewriteInput input = new RewriteInput();
        input.setRulesetPrefix("/test/lint/does-not-exist");
        input.setPaths(List.of("target"));
        assertThrows(NopRefactorException.class, () -> bizModel.applyRewrite(input));
    }
}
