package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.flow.ChangeAnalysisResult;
import io.nop.code.service.api.ICodeIndexService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-6.7-009 (plan 2401): {@code analyzeChanges} must run git with the local
 * filesystem path, not the persisted vfs form ("file:/...").
 * <p>
 * The index's rootPath is persisted through {@code resolveVfsPath} as
 * "file:&lt;abs&gt;". Before the fix that string was handed to
 * {@code ProcessBuilder.directory(...)}, so every call failed to even start git
 * and silently returned an empty, non-truncated diff ("no changes"). This test
 * indexes a real git repository and asserts the baseline..target diff is
 * actually computed.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestAnalyzeChangesFileRootPath extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @TempDir
    Path tempDir;

    private void git(Path dir, String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed (" + code + "): " + out);
        }
    }

    private String head(Path dir) throws Exception {
        Process p = new ProcessBuilder("git", "rev-parse", "HEAD").directory(dir.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return out;
    }

    @Test
    void analyzeChangesUsesLocalWorkingDirectoryFromFileRootPath() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "test@example.com");
        git(repo, "config", "user.name", "test");

        Path javaFile = repo.resolve("A.java");
        Files.writeString(javaFile, "public class A { int x; }\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "c1");
        String baseline = head(repo);

        Files.writeString(javaFile, "public class A { int x; int y; }\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "c2");
        String target = head(repo);

        String indexId = "idx-analyze-rootpath";
        assertTrue(codeIndexService.indexDirectory(indexId, repo.toAbsolutePath().toString(), null) >= 1,
                "index must contain A.java");

        ChangeAnalysisResult result = codeIndexService.analyzeChanges(indexId, baseline, target);

        assertNotNull(result);
        assertFalse(result.isTruncated(),
                "git ran successfully; the diff must not be marked truncated (pre-fix it silently failed)");
        assertTrue(result.getChangedFiles().contains("A.java"),
                "the A.java change must be detected; changedFiles=" + result.getChangedFiles());
    }
}
