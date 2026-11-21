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
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
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

    /**
     * Standard bare-repo directory convention: registry roots in the wild hold
     * repos as {@code <name>.git} directories (git-daemon / git-http-backend
     * layout). URL {@code /<name>.git} used to resolve to disk {@code <root>/<name>}
     * only and 403'd on this layout (live audit 2026-11-21).
     */
    @Test
    void servesDotGitBareDirectoryLayout() throws Exception {
        Path root2 = base.resolve("repos2");
        Files.createDirectories(root2);
        Path bare = root2.resolve("proj.git");
        // bare repo seeded via clone --bare from the worktree repo
        assertEquals(0, git(base, "clone", "--bare", "-q",
                base.resolve("repos/proj").toAbsolutePath().toString(),
                bare.toAbsolutePath().toString()).exit());

        NopGitHttpServer server2 = new NopGitHttpServer(GitServerConfig.of(0, root2));
        int port2 = server2.start();
        try {
            Path work = base.resolve("workdotgit");
            assertEquals(0, git(base, "clone", "-q",
                    "http://127.0.0.1:" + port2 + "/proj.git",
                    work.toAbsolutePath().toString()).exit());
            Files.writeString(work.resolve("a.txt"), "changed\n");
            assertEquals(0, git(work, "add", ".").exit());
            assertEquals(0, git(work, "commit", "-m", "bare layout").exit());
            assertEquals(0, git(work, "checkout", "-q", "-b", "devdotgit").exit());
            assertEquals(0, git(work, "push", "origin", "devdotgit").exit());
            assertEquals(sha(work, "HEAD"),
                    sha(bare, "refs/heads/devdotgit"));
        } finally {
            server2.stop();
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

    // ------------------------------------------------------------------
    // plan 2582: default loopback binding, token access control, non-heads
    // ref protection
    // ------------------------------------------------------------------

    @Test
    void loopbackIsTheDefaultBinding() throws Exception {
        // the running server's connector must be bound to the loopback host
        var connectorField = NopGitHttpServer.class.getDeclaredField("server");
        connectorField.setAccessible(true);
        org.eclipse.jetty.server.Server jetty =
                (org.eclipse.jetty.server.Server) connectorField.get(server);
        assertTrue(jetty.getConnectors().length > 0, "server has connectors");
        for (var connector : jetty.getConnectors()) {
            var host = ((org.eclipse.jetty.server.ServerConnector) connector).getHost();
            assertEquals("127.0.0.1", host, "default binding must be loopback");
        }
    }

    @Test
    void tokenAuthRejectsUnauthorizedWritesAndAcceptsAuthorizedOnes() throws Exception {
        Path root = base.resolve("token-repos");
        Path origin = root.resolve("proj");
        Files.createDirectories(origin);
        try (Git g = Git.init().setDirectory(origin.toFile())
                .setInitialBranch("master").call()) {
            Files.writeString(origin.resolve("a.txt"), "one\n");
            PersonIdent ident = new PersonIdent("Fixture User", "fixture@example.com",
                    new Date(1700000000000L), TimeZone.getTimeZone("GMT+08:00"));
            g.add().addFilepattern(".").call();
            g.commit().setMessage("base").setAuthor(ident).setCommitter(ident).call();
        }
        NopGitHttpServer tokenServer = new NopGitHttpServer(
                GitServerConfig.of(0, root, "s3cret"));
        int tokenPort = tokenServer.start();
        try {
            Path work = base.resolve("token-work");
            // clone without the token: rejected (401) before the endpoint
            GitResult noAuth = git(base, "clone",
                    "http://127.0.0.1:" + tokenPort + "/proj.git",
                    work.toAbsolutePath().toString());
            assertNotEquals(0, noAuth.exit(), "unauthenticated clone must be rejected");

            // with the bearer token everything works, including push
            GitResult authClone = git(base,
                    "-c", "http.extraHeader=Authorization: Bearer s3cret",
                    "clone", "http://127.0.0.1:" + tokenPort + "/proj.git",
                    work.toAbsolutePath().toString());
            assertEquals(0, authClone.exit(), "authorized clone failed: " + authClone.output());
            Files.writeString(work.resolve("b.txt"), "hello\n");
            assertEquals(0, git(work, "add", ".").exit());
            assertEquals(0, git(work, "commit", "-m", "authed").exit());
            assertEquals(0, git(work, "checkout", "-b", "dev").exit());
            GitResult authPush = git(work,
                    "-c", "http.extraHeader=Authorization: Bearer s3cret",
                    "push", "origin", "dev");
            assertEquals(0, authPush.exit(), "authorized push failed: " + authPush.output());

            // an unauthenticated push from a second clone of the plain server
            // would be a different registry; here the rejection path is the
            // unauthorized clone above plus a direct write attempt
            GitResult wrongToken = git(work,
                    "-c", "http.extraHeader=Authorization: Bearer wrong",
                    "push", "origin", "dev:dev2");
            assertNotEquals(0, wrongToken.exit(), "wrong-token push must be rejected");
        } finally {
            tokenServer.stop();
        }
    }

    @Test
    void protectedTagsRejectDeletionAndRewrite() {
        // plan 2582: non-heads refs are inside the protection perimeter —
        // deletion and forced rewrite of a protected tag name are rejected
        var hook = new ProtectedBranchPreReceiveHook(
                java.util.List.of("main", "master", "release/*", "stable"));
        var delete = new org.eclipse.jgit.transport.ReceiveCommand(
                org.eclipse.jgit.lib.ObjectId.zeroId(),
                org.eclipse.jgit.lib.ObjectId.zeroId(),
                "refs/tags/stable", org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE);
        var rewrite = new org.eclipse.jgit.transport.ReceiveCommand(
                org.eclipse.jgit.lib.ObjectId.fromString(
                        "0123456789abcdef0123456789abcdef01234567"),
                org.eclipse.jgit.lib.ObjectId.fromString(
                        "fedcba9876543210fedcba9876543210fedcba98"),
                "refs/tags/stable", org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
        hook.onPreReceive(null, java.util.List.of(delete, rewrite));
        assertEquals(org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON,
                delete.getResult());
        assertTrue(delete.getMessage().contains("protected tag"), delete.getMessage());
        assertEquals(org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON,
                rewrite.getResult());
        assertTrue(rewrite.getMessage().contains("protected tag"), rewrite.getMessage());
    }

    @Test
    void nonProtectedTagDeletionStillPasses() {
        var hook = new ProtectedBranchPreReceiveHook(
                java.util.List.of("main", "master", "release/*"));
        var command = new org.eclipse.jgit.transport.ReceiveCommand(
                org.eclipse.jgit.lib.ObjectId.zeroId(),
                org.eclipse.jgit.lib.ObjectId.zeroId(),
                "refs/tags/tmp-build", org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE);
        try {
            hook.onPreReceive(null, java.util.List.of(command));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals(org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED,
                command.getResult());
    }
}
