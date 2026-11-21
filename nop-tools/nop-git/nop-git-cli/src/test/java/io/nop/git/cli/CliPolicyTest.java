package io.nop.git.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end CLI interception point (design 03 section 6 point 1): policy
 * yaml in the repository drives deny/review before the facade runs; the
 * audit log records the outcome; inactive (no policy file) deployments stay
 * byte-identical to Wave 2 behavior.
 */
class CliPolicyTest {

    @TempDir
    Path base;

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    private int run(Path repo, String... args) {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        String[] full = new String[args.length + 2];
        full[0] = "-C";
        full[1] = repo.toAbsolutePath().toString();
        System.arraycopy(args, 0, full, 2, args.length);
        return GitCompatCli.run(full,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private Path repoWithPolicy(String yaml) throws Exception {
        Path repo = base.resolve("repo-" + Math.abs(yaml.hashCode()));
        Files.createDirectories(repo.resolve(".nop-git"));
        Files.writeString(repo.resolve(".nop-git/policy.yaml"), yaml);
        Files.writeString(repo.resolve("f.txt"), "content\n");
        IGitRepositorySmokeHelper.initRepo(repo);
        return repo;
    }

    @Test
    void enforceDenyExits1WithContractLine() throws Exception {
        Path repo = repoWithPolicy("""
                mode: enforce
                operations:
                  deny: [add]
                  allow: [status]
                audit_log: ".nop-git/audit.log"
                """);
        int exit = run(repo, "add", ".");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8)
                .startsWith("fatal: policy denied: "));
        String audit = Files.readString(repo.resolve(".nop-git/audit.log"));
        assertTrue(audit.contains("\"normalizedOperation\":\"add\""));
        assertTrue(audit.contains("\"outcome\":\"denied\""));
        assertFalse(audit.contains("hypotheticalEffect"));
    }

    @Test
    void enforceDefaultDenyBlocksUnlistedOperations() throws Exception {
        Path repo = repoWithPolicy("""
                mode: enforce
                default_effect: deny
                operations:
                  allow: [status, diff, log, branch, rev-parse, config, add]
                  review: [commit]
                audit_log: ".nop-git/audit.log"
                review_timeout_seconds: 0
                """);
        int exit = run(repo, "commit", "-m", "x");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"));
    }

    @Test
    void observeModeExecutesAndRecordsHypothetical() throws Exception {
        Path repo = repoWithPolicy("""
                mode: observe
                operations:
                  deny: [add]
                audit_log: ".nop-git/audit.log"
                """);
        int exit = run(repo, "add", ".");
        assertEquals(0, exit);
        String audit = Files.readString(repo.resolve(".nop-git/audit.log"));
        assertTrue(audit.contains("\"hypotheticalEffect\":\"DENY\""));
        assertTrue(audit.contains("\"outcome\":\"ok\""));
    }

    @Test
    void noPolicyFileKeepsWave2BehaviorAndWritesNoAudit() throws Exception {
        Path repo = base.resolve("plain");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("f.txt"), "content\n");
        IGitRepositorySmokeHelper.initRepo(repo);
        int exit = run(repo, "add", ".");
        assertEquals(0, exit);
        assertFalse(Files.exists(repo.resolve(".nop-git")));
        assertFalse(Files.exists(repo.resolve("audit.log")));
    }

    @Test
    void pathDenyBlocksSensitiveFile() throws Exception {
        Path repo = repoWithPolicy("""
                mode: enforce
                default_effect: allow
                path_rules:
                  - operation: [add]
                    deny_paths: ["**/.env*"]
                audit_log: ".nop-git/audit.log"
                """);
        Files.writeString(repo.resolve(".env"), "secret\n");
        int exit = run(repo, "add", ".env");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"));
    }

    // ------------------------------------------------------------------
    // plan 2582: bypass-path regression nails (deny asserted per path)
    // ------------------------------------------------------------------

    private static final String PROTECTED_PUSH_POLICY = """
            mode: enforce
            default_effect: deny
            operations:
              allow: [status, diff, log, branch, rev-parse, config, add, commit, push, fetch]
            option_rules:
              - operation: push
                deny_flags: ["--force", "--force-with-lease", "--mirror", "--delete"]
            protected_branches:
              patterns: ["main", "master", "release/*"]
              rules:
                allow_direct_push: false
                allow_force_push: false
                allow_delete: false
            audit_log: ".nop-git/audit.log"
            review_timeout_seconds: 0
            """;

    @Test
    void cliRefspecPlusPrefixIsDenied() throws Exception {
        Path repo = repoWithPolicy(PROTECTED_PUSH_POLICY);
        int exit = run(repo, "push", "origin", "+master:master");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"),
                err.toString(StandardCharsets.UTF_8));
        String audit = Files.readString(repo.resolve(".nop-git/audit.log"));
        assertTrue(audit.contains("\"outcome\":\"denied\""));
        assertTrue(audit.contains("--force"), "refspec + must surface as --force in audit");
    }

    @Test
    void cliForceWithLeaseOnProtectedBranchIsDenied() throws Exception {
        Path repo = repoWithPolicy(PROTECTED_PUSH_POLICY);
        int exit = run(repo, "push", "--force-with-lease", "origin", "master");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"));
    }

    @Test
    void cliMissingRefspecIsDeniedOnProtectedCurrentBranch() throws Exception {
        Path repo = repoWithPolicy(PROTECTED_PUSH_POLICY);
        // current branch master (protected), "push origin" without refspec
        int exit = run(repo, "push", "origin");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void cliWildcardRefspecIsDenied() throws Exception {
        Path repo = repoWithPolicy(PROTECTED_PUSH_POLICY);
        int exit = run(repo, "push", "origin", "refs/heads/*:refs/heads/*");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"));
    }

    @Test
    void cliSwitchAliasNormalizesToCheckoutOperation() throws Exception {
        Path repo = repoWithPolicy("""
                mode: enforce
                default_effect: deny
                operations:
                  allow: [status, diff, log, branch, rev-parse, config, add, commit]
                  deny: [checkout]
                audit_log: ".nop-git/audit.log"
                review_timeout_seconds: 0
                """);
        IGitRepositorySmokeHelper.initRepo(repo);
        // 'switch' used to bypass a checkout deny through operation-name drift
        int exit = run(repo, "switch", "master");
        assertEquals(1, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("policy denied"),
                err.toString(StandardCharsets.UTF_8));
        String audit = Files.readString(repo.resolve(".nop-git/audit.log"));
        assertTrue(audit.contains("\"normalizedOperation\":\"checkout\""));
    }

    @Test
    void cliWorktreeSubcommandNormalizesOperationName() throws Exception {
        Path repo = repoWithPolicy("""
                mode: enforce
                default_effect: deny
                operations:
                  allow: [status, diff, log, branch, rev-parse, config, add, commit, worktree-list]
                audit_log: ".nop-git/audit.log"
                review_timeout_seconds: 0
                """);
        IGitRepositorySmokeHelper.initRepo(repo);
        // default_effect deny: raw 'worktree list' as operation would be denied;
        // normalized worktree-list is allowed
        int exit = run(repo, "worktree", "list");
        assertEquals(0, exit, err.toString(StandardCharsets.UTF_8));
    }
}
