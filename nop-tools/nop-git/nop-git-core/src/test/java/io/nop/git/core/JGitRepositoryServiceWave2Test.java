package io.nop.git.core;

import io.nop.git.api.DiffOptions;
import io.nop.git.api.GitConfigScope;
import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.NopGitDiffEntry;
import io.nop.git.api.NopGitStatus;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;

import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 2 facade additions: rename detection, config read/write, topLevel,
 * remote refs, unified diff text.
 */
class JGitRepositoryServiceWave2Test {

    @TempDir
    Path dir;

    private static SystemReader originalReader;

    private static Path globalConfigFile;

    /**
     * Redirect the JGit "user config" to a temp file so GLOBAL scope tests
     * never touch the developer's real ~/.gitconfig.
     */
    @BeforeAll
    static void isolateGlobalConfig(@TempDir Path tmp) throws IOException {
        globalConfigFile = tmp.resolve("gitconfig-global");
        Files.writeString(globalConfigFile, "");
        originalReader = SystemReader.getInstance();
        SystemReader.setInstance(new SystemReader() {
            private final SystemReader delegate = originalReader;

            @Override
            public String getHostname() {
                return delegate.getHostname();
            }

            @Override
            public String getenv(String variable) {
                return delegate.getenv(variable);
            }

            @Override
            public String getProperty(String key) {
                return delegate.getProperty(key);
            }

            @Override
            public FileBasedConfig openUserConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, globalConfigFile.toFile(), fs);
            }

            @Override
            public FileBasedConfig openSystemConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, tmp.resolve("gitconfig-system").toFile(), fs);
            }

            @Override
            public FileBasedConfig openJGitConfig(Config parent, FS fs) {
                return delegate.openJGitConfig(parent, fs);
            }

            @Override
            public long getCurrentTime() {
                return delegate.getCurrentTime();
            }

            @Override
            public int getTimezone(long when) {
                return delegate.getTimezone(when);
            }
        });
    }

    @AfterAll
    static void restoreReader() {
        SystemReader.setInstance(originalReader);
    }

    private void file(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void stagedRenameIsDetectedInStatusAndDiff() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            file("a.txt", "one\ntwo\nthree\n");
            svc.add(".");
            svc.commit("base", "t", "t@example.com");

            // git mv equivalent: move file + restage
            Files.move(dir.resolve("a.txt"), dir.resolve("b.txt"));
            svc.add("a.txt");
            svc.add("b.txt");

            NopGitStatus status = svc.status();
            assertEquals(1, status.stagedRenames().size());
            assertEquals("a.txt", status.stagedRenames().get(0).oldPath());
            assertEquals("b.txt", status.stagedRenames().get(0).newPath());
            // rename halves must not appear as add/remove
            assertFalse(status.stagedAdded().contains("b.txt"));
            assertFalse(status.stagedRemoved().contains("a.txt"));

            List<NopGitDiffEntry> cached = svc.diff(DiffOptions.forCached());
            assertEquals(1, cached.size());
            assertEquals(io.nop.git.api.ChangeType.RENAMED, cached.get(0).changeType());
            assertEquals("a.txt", cached.get(0).oldPath());
            assertEquals("b.txt", cached.get(0).newPath());
        }
    }

    @Test
    void unifiedDiffRendersNewFileAndModification() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            file("a.txt", "one\ntwo\nthree\n");
            svc.add(".");
            svc.commit("base", "t", "t@example.com");

            file("a.txt", "one\ntwo\nthree\nfour\n");
            file("new.txt", "hello\n");
            svc.add("new.txt");

            // a.txt is unstaged: its hunk lives in the worktree diff
            String worktreeText = String.join("\n", svc.unifiedDiff(DiffOptions.worktree()));
            assertTrue(worktreeText.contains("diff --git a/a.txt b/a.txt"));
            assertTrue(worktreeText.contains("--- a/a.txt"));
            assertTrue(worktreeText.contains("+++ b/a.txt"));
            assertTrue(worktreeText.contains("@@ -1,3 +1,4 @@"));
            assertTrue(worktreeText.contains("+four"));

            // new.txt is staged: the cached diff shows the new-file header
            String cachedText = String.join("\n", svc.unifiedDiff(DiffOptions.forCached()));
            assertTrue(cachedText.contains("diff --git a/new.txt b/new.txt"));
            assertTrue(cachedText.contains("new file mode 100644"));
            assertTrue(cachedText.contains("--- /dev/null"));
            assertTrue(cachedText.contains("+++ b/new.txt"));
            assertTrue(cachedText.contains("@@ -0,0 +1 @@"));
            assertTrue(cachedText.matches("(?s).*index [0-9a-f]{7}\\.\\.[0-9a-f]{7}.*"));
            assertFalse(cachedText.contains("\u001b["), "no ANSI escapes");
        }
    }

    @Test
    void configReadWriteRepositoryAndGlobal() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            svc.configSet("user.name", "tester", GitConfigScope.GLOBAL);
            assertEquals("tester", svc.configGet("user.name", GitConfigScope.GLOBAL));
            assertNull(svc.configGet("user.name", GitConfigScope.REPOSITORY));

            svc.configSet("user.email", "t@example.com", GitConfigScope.REPOSITORY);
            assertEquals("t@example.com", svc.configGet("user.email", GitConfigScope.REPOSITORY));
            assertNull(svc.configGet("user.email", GitConfigScope.GLOBAL));

            // global config went to the isolated temp file, not ~/.gitconfig
            assertFalse(Files.readAllLines(Path.of(System.getProperty("user.home"),
                    ".gitconfig")).contains("tester"));
        }
    }

    @Test
    void topLevelReturnsRealWorkingTreePath() {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            assertNotNull(svc.topLevel());
            assertTrue(new File(svc.topLevel()).isDirectory());
        }
    }

    @Test
    void branchListAllIncludesRemoteRefs() throws IOException {
        try (IGitRepositoryService svc = IGitRepositoryService.init(dir.toFile())) {
            file("a.txt", "x\n");
            svc.add(".");
            svc.commit("base", "t", "t@example.com");

            // fabricate a remote-tracking ref + symbolic origin/HEAD
            org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(dir.toFile());
            org.eclipse.jgit.lib.RefUpdate update = git.getRepository()
                    .updateRef("refs/remotes/origin/master");
            update.setNewObjectId(git.getRepository().resolve("HEAD"));
            update.update();
            org.eclipse.jgit.lib.RefUpdate sym = git.getRepository()
                    .updateRef("refs/remotes/origin/HEAD");
            sym.link("refs/remotes/origin/master");
            sym.update();
            git.close();

            List<io.nop.git.api.NopGitRef> refs = svc.branchListAll();
            assertEquals(3, refs.size());
            io.nop.git.api.NopGitRef originHead = refs.stream()
                    .filter(r -> r.name().equals("refs/remotes/origin/HEAD")).findFirst().orElseThrow();
            assertTrue(originHead.isRemote());
            assertEquals("refs/remotes/origin/master", originHead.symbolicTarget());
            assertTrue(refs.stream().anyMatch(r -> r.name().equals("refs/heads/master")
                    && r.isBranch() && !r.isRemote()));
        }
    }
}
