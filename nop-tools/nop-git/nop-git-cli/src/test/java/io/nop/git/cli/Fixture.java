package io.nop.git.cli;

import io.nop.git.api.IGitRepositoryService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.TimeZone;

/**
 * Deterministic test fixtures: commits use a pinned identity and a fixed
 * timestamp + timezone, so object ids are stable across runs and machines
 * (golden files stay valid). Fixtures are built through JGit directly; the
 * code under test (CLI parity, smoke checks) then reads the same repository.
 */
public final class Fixture {

    static final String NAME = "Fixture User";
    static final String EMAIL = "fixture@example.com";
    static final long FIXED_EPOCH_SECONDS = 1700000000L;
    static final TimeZone TZ = TimeZone.getTimeZone("GMT+08:00");

    private Fixture() {
    }

    public static PersonIdent ident() {
        return new PersonIdent(NAME, EMAIL, new Date(FIXED_EPOCH_SECONDS * 1000L), TZ);
    }

    public static Path createRepo(Path base, String name) throws IOException {
        Path dir = base.resolve(name);
        Files.createDirectories(dir);
        IGitRepositoryService.init(dir.toFile(), "master").close();
        return dir;
    }

    /**
     * Commit through JGit with the pinned identity so SHAs are deterministic.
     */
    public static void commit(Path repo, String message, String... paths) throws Exception {
        try (Git git = Git.open(repo.toFile())) {
            var cmd = git.add();
            for (String path : paths) {
                cmd.addFilepattern(path);
            }
            cmd.call();
            git.commit()
                    .setMessage(message)
                    .setAuthor(ident())
                    .setCommitter(ident())
                    .call();
        }
    }

    /** Repo with one commit ("base") containing a.txt. */
    public static Path cleanSingleCommit(Path base, String name) throws Exception {
        Path dir = createRepo(base, name);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\n");
        commit(dir, "base commit", "a.txt");
        return dir;
    }

    /** Clean repo + unstaged modification of a.txt and an untracked new.txt. */
    public static Path dirtyUnstaged(Path base, String name) throws Exception {
        Path dir = cleanSingleCommit(base, name);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\nfour\n");
        Files.writeString(dir.resolve("new.txt"), "hello\n");
        return dir;
    }

    /** Clean repo + staged modification of a.txt plus a staged new file b.txt. */
    public static Path dirtyStaged(Path base, String name) throws Exception {
        Path dir = cleanSingleCommit(base, name);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\nfour\nfive\n");
        Files.writeString(dir.resolve("b.txt"), "hello\nworld\n");
        try (Git git = Git.open(dir.toFile())) {
            git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
        }
        return dir;
    }

    /** Clean repo + untracked file and an untracked directory. */
    public static Path untrackedContent(Path base, String name) throws Exception {
        Path dir = cleanSingleCommit(base, name);
        Files.writeString(dir.resolve("u.txt"), "u\n");
        Files.createDirectories(dir.resolve("newdir"));
        Files.writeString(dir.resolve("newdir/x.txt"), "x\n");
        return dir;
    }

    /** Clean repo + staged pure rename a.txt -> b.txt. */
    public static Path stagedRename(Path base, String name) throws Exception {
        Path dir = cleanSingleCommit(base, name);
        Files.move(dir.resolve("a.txt"), dir.resolve("b.txt"));
        try (Git git = Git.open(dir.toFile())) {
            git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
        }
        return dir;
    }

    /** Clean repo + fabricated remote-tracking refs (origin/master + symbolic HEAD). */
    public static Path withRemote(Path base, String name) throws Exception {
        Path dir = cleanSingleCommit(base, name);
        try (Git git = Git.open(dir.toFile())) {
            var repository = git.getRepository();
            var update = repository.updateRef("refs/remotes/origin/master");
            update.setNewObjectId(repository.resolve("HEAD"));
            update.update();
            var sym = repository.updateRef("refs/remotes/origin/HEAD");
            sym.link("refs/remotes/origin/master");
            sym.update();
        }
        return dir;
    }
}
