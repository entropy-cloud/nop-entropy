package io.nop.git.server;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP smart-protocol integration test (roadmap item 28): a real git client
 * talks to the embedded server over HTTP; push goes through receive-pack
 * with the protected-branch PreReceiveHook.
 */
class NopGitHttpServerIntegrationTest {

    @TempDir
    Path base;

    private NopGitHttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        Path root = base.resolve("repos");
        Path origin = root.resolve("proj");
        Files.createDirectories(origin);
        try (Git git = Git.init().setDirectory(origin.toFile())
                .setInitialBranch("master").call()) {
            Files.writeString(origin.resolve("a.txt"), "one\ntwo\nthree\n");
            PersonIdent ident = new PersonIdent("Fixture User", "fixture@example.com",
                    new Date(1700000000000L), TimeZone.getTimeZone("GMT+08:00"));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("base").setAuthor(ident).setCommitter(ident).call();
        }
        server = new NopGitHttpServer(GitServerConfig.of(0, root));
        port = server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null)
            server.stop();
    }

    private record GitResult(int exit, String output) {
    }

    private GitResult git(Path dir, String... args) throws Exception {
        String[] real = new String[args.length + 5];
        real[0] = "git";
        real[1] = "-c";
        real[2] = "user.name=Fixture User";
        real[3] = "-c";
        real[4] = "user.email=fixture@example.com";
        System.arraycopy(args, 0, real, 5, args.length);
        ProcessBuilder pb = new ProcessBuilder(real);
        pb.directory(dir.toFile());
        pb.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String out;
        try (var is = process.getInputStream()) {
            out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        assertTrue(finished, "git command timed out: " + String.join(" ", args));
        return new GitResult(process.exitValue(), out);
    }

    private String sha(Path repo, String rev) throws IOException {
        try (Git git = Git.open(repo.toFile())) {
            var id = git.getRepository().resolve(rev);
            return id == null ? null : id.name();
        }
    }

    @Test
    void cloneCommitPushRoundtripOverHttp() throws Exception {
        Path work = base.resolve("work");
        GitResult clone = git(base, "clone",
                "http://127.0.0.1:" + port + "/proj.git", work.toAbsolutePath().toString());
        assertEquals(0, clone.exit(), "clone failed: " + clone.output());
        assertTrue(Files.exists(work.resolve("a.txt")));

        Files.writeString(work.resolve("c.txt"), "hello\n");
        assertEquals(0, git(work, "add", ".").exit());
        assertEquals(0, git(work, "commit", "-m", "via http").exit());
        assertEquals(0, git(work, "checkout", "-b", "dev").exit());
        GitResult push = git(work, "push", "origin", "dev");
        assertEquals(0, push.exit(), "push failed: " + push.output());

        // the server-side repository received the branch
        assertEquals(sha(work, "HEAD"), sha(base.resolve("repos/proj"), "refs/heads/dev"));
    }

    @Test
    void protectedBranchRulesRejectDirectForceAndDelete() throws Exception {
        Path work = base.resolve("work2");
        assertEquals(0, git(base, "clone",
                "http://127.0.0.1:" + port + "/proj.git",
                work.toAbsolutePath().toString()).exit());

        // direct push to protected master is denied
        Files.writeString(work.resolve("a.txt"), "changed\n");
        assertEquals(0, git(work, "add", ".").exit());
        assertEquals(0, git(work, "commit", "-m", "direct").exit());
        GitResult direct = git(work, "push", "origin", "master");
        assertNotEquals(0, direct.exit(), "direct push to master must be rejected");
        assertTrue(direct.output().contains("protected branch"), direct.output());

        // force push (non-fast-forward) is denied
        GitResult force = git(work, "push", "--force", "origin", "master");
        assertNotEquals(0, force.exit(), "force push to master must be rejected");
        assertTrue(force.output().contains("protected branch"), force.output());

        // deletion is denied
        GitResult delete = git(work, "push", "origin", ":master");
        assertNotEquals(0, delete.exit(), "delete of master must be rejected");
        assertTrue(delete.output().contains("protected branch"), delete.output());
    }
}
