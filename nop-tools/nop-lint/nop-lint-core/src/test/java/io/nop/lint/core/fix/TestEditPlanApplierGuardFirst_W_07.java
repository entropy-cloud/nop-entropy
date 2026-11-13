package io.nop.lint.core.fix;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.SourceRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard-first write-order contract (plan 2531 W-07, red⑥ — M-P2-6,
 * red before the fix and green after): the re-parse guard's inputs
 * (source/next) are both in memory, so a rejected plan must be rejected
 * BEFORE anything reaches the disk. Pre-fix the write landed first
 * ({@code atomicWrite} before the guard), so a rejected plan's broken
 * content hit the directory — visible here as a write attempt into a
 * write-rejecting directory that fails the whole apply. Post-fix the guard
 * rejects in memory: the apply returns the rolledBack ending and the
 * directory never sees a single write.
 */
public class TestEditPlanApplierGuardFirst_W_07 {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    @TempDir
    Path dir;

    private static Fix edit(String ruleId, String source, String target, String replacement) {
        int start = source.indexOf(target);
        return new Fix(new SourceRange(start, start + target.length()), replacement,
                ruleId, "desc", 0);
    }

    @Test
    public void testW_07_guardRejectionLeavesZeroFilesOnDiskWhenTheDirectoryRejectsWrites()
            throws Exception {
        String source = "class T { void m() { foo(); } }";
        Path file = dir.resolve("T.java");
        Files.writeString(file, source);
        // the rewrite replaces a statement with a stray identifier — the
        // parse produces an ERROR node the original did not have, so the
        // guard must reject the plan
        Fix breaking = edit("op/w07", source, "foo();", "grobl");

        Set<PosixFilePermission> original = Files.getPosixFilePermissions(dir);
        Set<PosixFilePermission> readOnly = Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(dir, readOnly);
        try {
            EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(file,
                    source.getBytes(StandardCharsets.UTF_8), List.of(breaking), JAVA, false);

            assertTrue(result.rolledBack(),
                    "the guard rejects the plan in memory and reports the returned ending");
            assertEquals(0, result.appliedEdits(),
                    "a rejected plan counts zero surviving edits");
            assertEquals(source, Files.readString(file),
                    "the target keeps its input content");
        } finally {
            Files.setPosixFilePermissions(dir, original);
        }

        try (var stream = Files.list(dir)) {
            List<Path> others = stream
                    .filter(p -> !p.getFileName().toString().equals("T.java"))
                    .toList();
            assertTrue(others.isEmpty(),
                    "zero temp creation: the write path never ran: " + others);
        }
    }
}
