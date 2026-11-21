package io.nop.git.policy;

import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.NopGitException;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Force/direct-push bypass regression nails (plan 2582): every previously
 * bypassing path must evaluate to DENY against a protected branch.
 * Covers the refspec "+" prefix, --force-with-lease, the missing refspec
 * ("push -u origin" backfill), the wildcard refspec, and the facade entry.
 */
class GitPolicyBypassTest {

    @TempDir
    Path base;

    private static final String PROTECTED_POLICY = """
            version: "1.0"
            mode: enforce
            default_effect: deny
            audit_log: ".nop-git/audit.log"
            review_timeout_seconds: 30
            operations:
              allow: [status, diff, log, add, commit, push, fetch, remote]
            option_rules:
              - operation: push
                deny_flags: ["--force", "--force-with-lease", "--mirror", "--delete"]
            protected_branches:
              patterns: ["main", "master", "release/*"]
              rules:
                allow_direct_push: false
                allow_force_push: false
                allow_delete: false
            """;

    private IGitPolicyEngine engine(String yaml) {
        GitPolicyConfig config = GitPolicyConfig.merge(
                java.util.List.of(GitPolicyLoader.load(write(yaml))));
        StringWriter reviewOut = new StringWriter();
        return DefaultGitPolicyEngine.fromConfig(config, base,
                new BufferedReader(new StringReader("")),
                () -> Long.MAX_VALUE / 2, reviewOut);
    }

    private Path write(String yaml) {
        try {
            Path file = base.resolve("policy-" + System.nanoTime() + ".yaml");
            Files.writeString(file, yaml);
            return file;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void refspecPlusPrefixNormalizesToForceAndDenies() {
        var engine = engine(PROTECTED_POLICY);
        // "+main:main" used to land in path rules and bypass branch protection
        var request = GitRequests.builder("push", "/work/repo", "push origin +main:main")
                .remote("origin", null)
                .refspec("+main:main").build();
        GitVerdict verdict = engine.evaluate(request);
        assertEquals(GitVerdict.Effect.DENY, verdict.effect());
        assertTrue(request.flags().contains("--force"), "refspec + must normalize to --force");
        assertEquals("main", request.targetBranch());
    }

    @Test
    void refspecPlusPrefixShortFormDenies() {
        var engine = engine(PROTECTED_POLICY);
        // "+main" is force-push shorthand for main:main
        var request = GitRequests.builder("push", "/work/repo", "push origin +main")
                .remote("origin", null)
                .refspec("+main").build();
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(request).effect());
    }

    @Test
    void forceWithLeaseCarriesForceSemantics() {
        var engine = engine(PROTECTED_POLICY);
        var request = GitRequests.builder("push", "/work/repo", "push --force-with-lease origin main")
                .flag("--force-with-lease=refs/heads/main")
                .remote("origin", null)
                .targetBranch("main").build();
        GitVerdict verdict = engine.evaluate(request);
        assertEquals(GitVerdict.Effect.DENY, verdict.effect());
        assertTrue(request.flags().contains("--force"),
                "--force-with-lease must normalize to force semantics for branch protection");
    }

    @Test
    void wildcardRefspecIsStrictestDeny() {
        var engine = engine(PROTECTED_POLICY);
        // refs/heads/*:refs/heads/* may touch any protected branch
        var request = GitRequests.builder("push", "/work/repo", "push origin 'refs/heads/*:refs/heads/*'")
                .remote("origin", null)
                .refspec("refs/heads/*:refs/heads/*").build();
        GitVerdict verdict = engine.evaluate(request);
        assertEquals(GitVerdict.Effect.DENY, verdict.effect());
        assertEquals("*", request.targetBranch());
    }

    @Test
    void wildcardRefspecDeniedThroughFacade() throws Exception {
        Path origin = initBare("origin.git");
        Path repo = initWorkRepo(PROTECTED_POLICY, "master");
        try (IGitRepositoryService svc = IGitRepositoryService.open(repo.toFile())) {
            svc.remoteAdd("origin", origin.toUri().toString());
            // wildcard push would overwrite every branch including protected ones
            NopGitException e = assertThrows(NopGitException.class,
                    () -> svc.push("origin", "refs/heads/*:refs/heads/*", false));
            assertTrue(e.getMessage().startsWith("policy denied"), e.getMessage());
        }
    }

    @Test
    void refspecPlusPrefixDeniedThroughFacade() throws Exception {
        Path origin = initBare("origin2.git");
        Path repo = initWorkRepo(PROTECTED_POLICY, "master");
        try (IGitRepositoryService svc = IGitRepositoryService.open(repo.toFile())) {
            svc.remoteAdd("origin", origin.toUri().toString());
            NopGitException e = assertThrows(NopGitException.class,
                    () -> svc.push("origin", "+master:master", false));
            assertTrue(e.getMessage().startsWith("policy denied"), e.getMessage());
        }
    }

    @Test
    void missingRefspecBackfillsCurrentBranchAndDeniesProtectedPush() throws Exception {
        Path origin = initBare("origin3.git");
        // current branch is protected master: "push origin" (no refspec) must
        // be evaluated against master and denied
        Path repo = initWorkRepo(PROTECTED_POLICY, "master");
        try (IGitRepositoryService svc = IGitRepositoryService.open(repo.toFile())) {
            svc.remoteAdd("origin", origin.toUri().toString());
            svc.add(".");
            svc.commit("base", "t", "t@example.com");
            NopGitException e = assertThrows(NopGitException.class,
                    () -> svc.push("origin", null, false));
            assertTrue(e.getMessage().startsWith("policy denied"), e.getMessage());
        }
    }

    @Test
    void pushUpstreamWithoutRefspecSucceedsOnUnprotectedBranch() throws Exception {
        Path origin = initBare("origin4.git");
        // current branch is the unprotected dev branch (no checkout needed)
        Path repo = initWorkRepo(PROTECTED_POLICY, "dev");
        // push is review in the builtin layer: approve it interactively via a
        // scripted stdin ("yes") instead of the environment variable
        IGitPolicyEngine engine = DefaultGitPolicyEngine.fromConfig(
                GitPolicyConfig.merge(java.util.List.of(
                        GitPolicyLoader.load(repo.resolve(".nop-git/policy.yaml")))),
                repo, new java.io.BufferedReader(new StringReader("yes\n")),
                System::nanoTime, new StringWriter());
        try (IGitRepositoryService svc = IGitRepositoryService.open(repo.toFile(), engine)) {
            svc.remoteAdd("origin", origin.toUri().toString());
            svc.add(".");
            svc.commit("base", "t", "t@example.com");
            // "push -u origin" (no refspec) is a legal operation: backfill the
            // current branch, push must succeed and write the upstream mapping
            svc.push("origin", null, true);
            assertEquals(svc.resolveRevision("HEAD"),
                    shaOf(origin.resolve("refs/heads/dev")));
            String config = Files.readString(repo.resolve(".git/config"));
            assertTrue(config.contains("[branch \"dev\"]"), config);
            assertTrue(config.contains("merge = refs/heads/dev"), config);
        }
    }

    private String shaOf(Path refFile) throws java.io.IOException {
        // bare ref files store the raw sha (packed refs are not produced here)
        return Files.readString(refFile).trim();
    }

    private Path initBare(String name) throws Exception {
        Path origin = base.resolve(name);
        try (Git git = Git.init().setBare(true).setDirectory(origin.toFile()).call()) {
            // bare origin
        }
        return origin;
    }

    private Path initWorkRepo(String yaml, String initialBranch) throws Exception {
        Path repo = base.resolve("work-" + System.nanoTime());
        Files.createDirectories(repo.resolve(".nop-git"));
        Files.writeString(repo.resolve(".nop-git/policy.yaml"), yaml);
        Files.writeString(repo.resolve("f.txt"), "content\n");
        IGitRepositoryService.init(repo.toFile(), initialBranch).close();
        return repo;
    }
}
