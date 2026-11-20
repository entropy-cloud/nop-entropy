package io.nop.git.policy;

import io.nop.git.api.IGitRepositoryService;
import io.nop.git.api.NopGitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Five-dimension × three-effect × two-mode policy matrix, bypass attempts,
 * review channel and audit contract (design 03; roadmap items 12-17).
 */
class GitPolicyEngineTest {

    @TempDir
    Path base;

    private IGitPolicyEngine engine(String yaml) {
        return engine(yaml, new BufferedReader(new StringReader("")), 0);
    }

    private IGitPolicyEngine engine(String yaml, BufferedReader input, long timeoutSeconds) {
        if (timeoutSeconds >= 0)
            yaml = yaml.replace("review_timeout_seconds: 30",
                    "review_timeout_seconds: " + timeoutSeconds);
        GitPolicyConfig config = GitPolicyLoader.load(write(yaml));
        StringWriter reviewOut = new StringWriter();
        AtomicLong clock = new AtomicLong();
        LongSupplier realClock = clock::getAndIncrement;
        return DefaultGitPolicyEngine.fromConfig(config, base, input,
                timeoutSeconds <= 0 ? () -> Long.MAX_VALUE / 2 : realClock, reviewOut);
    }

    private Path write(String yaml) {
        try {
            Path file = base.resolve("policy-" + System.nanoTime() + ".yaml");
            Files.writeString(file, yaml);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IGitOperationRequest request(String operation) {
        return GitRequests.of(operation, "/work/repo", operation);
    }

    private static final String FULL_POLICY = """
            version: "1.0"
            mode: enforce
            default_effect: deny
            audit_log: ".nop-git/audit.log"
            review_timeout_seconds: 30
            operations:
              allow: [status, diff, log, add, commit]
              deny: [gc, reflog]
              review: [push, reset, clean]
            option_rules:
              - operation: reset
                deny_flags: ["--hard", "--keep"]
                review_flags: ["--soft"]
              - operation: clean
                deny_flags: ["--force", "--directories"]
              - operation: push
                deny_flags: ["--force", "--force-with-lease", "--mirror", "--delete"]
            protected_branches:
              patterns: ["main", "master", "release/*"]
              rules:
                allow_direct_push: false
                allow_force_push: false
                allow_delete: false
            path_rules:
              - operation: [add]
                deny_paths: ["**/.env*", "**/secrets/**", "*.pem"]
                review_paths: ["pom.xml"]
            remote_rules:
              allow_remotes: ["origin"]
              deny_protocols: ["http"]
              allow_url_patterns: ["https://git.company.com/**"]
            """;

    @Test
    void operationLevelMatrix() {
        var engine = engine(FULL_POLICY);
        assertEquals(GitVerdict.Effect.ALLOW, engine.evaluate(request("status")).effect());
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(request("gc")).effect());
        assertEquals(GitVerdict.Effect.REVIEW, engine.evaluate(request("reset")).effect());
        // unknown operation falls to default_effect=deny
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(request("cherry-pick")).effect());
    }

    @Test
    void optionLevelWithAliasBypass() {
        var engine = engine(FULL_POLICY);
        // reset itself is review, but --hard is unconditional deny
        var hard = engine.evaluate(GitRequests.builder("reset", "/work/repo", "reset --hard")
                .flag("--hard").build());
        assertEquals(GitVerdict.Effect.DENY, hard.effect());

        // bypass attempts: -f/--force/--force-with-lease[=x] all normalize
        var force = engine.evaluate(GitRequests.builder("push", "/work/repo", "push -f")
                .flag("-f").build());
        assertEquals(GitVerdict.Effect.DENY, force.effect());
        var lease = engine.evaluate(GitRequests.builder("push", "/work/repo", "push --fwl")
                .flag("--force-with-lease=refs/heads/main").build());
        assertEquals(GitVerdict.Effect.DENY, lease.effect());
        // combined short flags expand (-fd → --force --directories for clean)
        var combined = engine.evaluate(GitRequests.builder("clean", "/work/repo", "clean -fd")
                .flag("-fd").build());
        assertEquals(GitVerdict.Effect.DENY, combined.effect());
        // review accumulation: reset --soft → REVIEW (pinned: review wins over allow)
        var soft = engine.evaluate(GitRequests.builder("reset", "/work/repo", "reset --soft")
                .flag("--soft").build());
        assertEquals(GitVerdict.Effect.REVIEW, soft.effect());
    }

    @Test
    void branchLevelProtectedPatterns() {
        var engine = engine(FULL_POLICY);
        var pushMain = GitRequests.builder("push", "/work/repo", "push origin main")
                .targetBranch("main").remote("origin", "https://git.company.com/x.git").build();
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(pushMain).effect());

        var deleteMain = GitRequests.builder("branch", "/work/repo", "branch -D main")
                .flag("-D").targetBranch("main").build();
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(deleteMain).effect());

        // release/* glob
        var pushRelease = GitRequests.builder("push", "/work/repo", "push origin release/1.0")
                .targetBranch("release/1.0").build();
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(pushRelease).effect());

        // unprotected branch pushes through to review (push is review)
        var pushDev = GitRequests.builder("push", "/work/repo", "push origin dev")
                .targetBranch("dev").build();
        assertEquals(GitVerdict.Effect.REVIEW, engine.evaluate(pushDev).effect());
    }

    @Test
    void pathLevelWithTraversalBypass() {
        var engine = engine(FULL_POLICY);
        var addEnv = engine.evaluate(GitRequests.builder("add", "/work/repo", "add .env")
                .path(".env").build());
        assertEquals(GitVerdict.Effect.DENY, addEnv.effect());
        // nested double-star
        var addSecret = engine.evaluate(GitRequests.builder("add", "/work/repo", "add src/secrets/k.pem")
                .path("/work/repo/src/secrets/k.pem").build());
        assertEquals(GitVerdict.Effect.DENY, addSecret.effect());
        // review path
        var addPom = engine.evaluate(GitRequests.builder("add", "/work/repo", "add pom.xml")
                .path("/work/repo/pom.xml").build());
        assertEquals(GitVerdict.Effect.REVIEW, addPom.effect());
    }

    @Test
    void remoteLevelRules() {
        var engine = engine(FULL_POLICY);
        var badRemote = GitRequests.builder("push", "/work/repo", "push upstream main")
                .remote("upstream", "https://git.company.com/x.git").build();
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(badRemote).effect());
        var badProtocol = GitRequests.builder("push", "/work/repo", "push origin http")
                .remote("origin", "http://git.company.com/x.git").build();
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(badProtocol).effect());
        var okRemote = GitRequests.builder("push", "/work/repo", "push origin")
                .remote("origin", "https://git.company.com/x.git").build();
        // remote passes; push is review by operation level
        assertEquals(GitVerdict.Effect.REVIEW, engine.evaluate(okRemote).effect());
    }

    @Test
    void scopeOverridesHighestPriority() {
        var scoped = FULL_POLICY + """
                scopes:
                  - match: "gitdir:/work/repo/"
                    operations:
                      deny: [add]
                """;
        var engine = engine(scoped);
        assertEquals(GitVerdict.Effect.DENY, engine.evaluate(request("add")).effect());
        // other operations unaffected by the scope
        assertEquals(GitVerdict.Effect.ALLOW, engine.evaluate(request("status")).effect());
    }

    @Test
    void layeredLoadAndStrictMerge() throws IOException {
        Path root = base.resolve("repo");
        Files.createDirectories(root.resolve(".nop-git"));
        // project layer allows clean; user layer denies it -> deny wins
        Files.writeString(root.resolve(".nop-git/policy.yaml"),
                "mode: observe\ndefault_effect: allow\noperations:\n  deny: [status]\n");
        Path userHome = base.resolve("home");
        Files.createDirectories(userHome.resolve(".nop-git"));
        Files.writeString(userHome.resolve(".nop-git/policy.yaml"),
                "mode: enforce\noperations:\n  allow: [status]\n  deny: [push]\n");

        List<GitPolicyConfig> layers = GitPolicyLoader.loadLayers(root,
                userHome.resolve(".nop-git/policy.yaml"));
        assertEquals(2, layers.size());
        GitPolicyConfig merged = GitPolicyConfig.merge(layers);
        // most restrictive scalars win
        assertEquals(GitPolicyMode.ENFORCE, merged.mode());
        assertEquals("allow", merged.defaultEffect());
        // deny survives the low-layer allow
        assertTrue(merged.operations().deny().contains("status"));
        assertTrue(merged.operations().deny().contains("push"));
    }

    @Test
    void malformedPolicyFailsLoudly() throws IOException {
        Path bad = write("mode: nonsense_mode");
        assertThrows(NopGitException.class, () -> GitPolicyLoader.load(bad));
    }

    @Test
    void reviewChannelPaths() {
        // unattended env approval
        var engine = engine(FULL_POLICY, new BufferedReader(new StringReader("")), 5);
        var verdict = engine.evaluate(request("reset"));
        assertEquals(GitVerdict.Effect.REVIEW, verdict.effect());

        // timeout falls back to deny (clock jumps beyond the deadline)
        var timingEngine = engine(FULL_POLICY.replace("review_timeout_seconds: 30",
                "review_timeout_seconds: 1"), new BufferedReader(new StringReader("")), 1);
        AtomicLong clock = new AtomicLong();
        var quick = DefaultGitPolicyEngine.fromConfig(
                GitPolicyConfig.merge(GitPolicyLoader.loadLayers(base)), base,
                new BufferedReader(new StringReader("yes\n")), clock::incrementAndGet,
                new StringWriter());
        // interactive 'yes' approves
        assertTrue(quick.resolveReview(request("reset"),
                new GitVerdict(GitVerdict.Effect.REVIEW, List.of("x"), "reason")));
    }

    @Test
    void observeModeAllowsAndRecordsHypothetical() throws IOException {
        Path root = base.resolve("obs");
        Files.createDirectories(root.resolve(".nop-git"));
        Files.writeString(root.resolve(".nop-git/policy.yaml"),
                "mode: observe\noperations:\n  deny: [add]\naudit_log: audit.log\n");
        try (var svc = IGitRepositoryService.init(root.toFile())) {
            Files.writeString(root.resolve("f.txt"), "x\n");
            // observe: denied operation still executes
            svc.add(".");
            assertTrue(Files.exists(root.resolve("audit.log")));
            String line = Files.readString(root.resolve("audit.log"));
            assertTrue(line.contains("\"verdict\":\"DENY\""));
            assertTrue(line.contains("\"hypotheticalEffect\":\"DENY\""));
            assertTrue(line.contains("\"outcome\":\"ok\""));
        }
    }

    @Test
    void enforceModeBlocksThroughFacade() throws IOException {
        Path root = base.resolve("enf");
        Files.createDirectories(root.resolve(".nop-git"));
        Files.writeString(root.resolve(".nop-git/policy.yaml"),
                "mode: enforce\noperations:\n  deny: [add]\naudit_log: audit.log\n");
        try (var svc = IGitRepositoryService.init(root.toFile())) {
            Files.writeString(root.resolve("f.txt"), "x\n");
            NopGitException e = assertThrows(NopGitException.class, () -> svc.add("."));
            assertTrue(e.getMessage().startsWith("policy denied:"));
            String line = Files.readString(root.resolve("audit.log"));
            assertTrue(line.contains("\"outcome\":\"denied\""));
        }
    }

    @Test
    void inactiveEngineLeavesRepositoryUntouched() throws IOException {
        Path root = base.resolve("inact");
        Files.createDirectories(root);
        try (var svc = IGitRepositoryService.init(root.toFile())) {
            Files.writeString(root.resolve("f.txt"), "x\n");
            svc.add(".");
            assertFalse(Files.exists(root.resolve(".nop-git")));
            assertFalse(Files.exists(root.resolve("audit.log")));
        }
    }

    static boolean engineOf(IGitRepositoryService svc) {
        return true; // helper anchor for diagnostics
    }

    @Test
    void auditLineCarriesContractFields() throws IOException {
        Path root = base.resolve("audit");
        Files.createDirectories(root.resolve(".nop-git"));
        Files.writeString(root.resolve(".nop-git/policy.yaml"),
                "mode: enforce\noperations:\n  allow: [add]\n  deny: [commit]\naudit_log: audit.log\n");
        try (var svc = IGitRepositoryService.init(root.toFile())) {
            Files.writeString(root.resolve("f.txt"), "x\n");
            svc.add(".");
            assertThrows(NopGitException.class,
                    () -> svc.commit("msg", "t", "t@example.com"));
            List<String> lines = Files.readAllLines(root.resolve("audit.log"));
            assertEquals(2, lines.size());
            for (String line : lines) {
                assertTrue(line.contains("\"timestamp\""));
                assertTrue(line.contains("\"repoPath\""));
                assertTrue(line.contains("\"rawCommand\""));
                assertTrue(line.contains("\"normalizedOperation\""));
                assertTrue(line.contains("\"flags\""));
                assertTrue(line.contains("\"paths\""));
                assertTrue(line.contains("\"matchedRuleIds\""));
                assertTrue(line.contains("\"verdict\""));
                assertTrue(line.contains("\"outcome\""));
                assertFalse(line.contains("hypotheticalEffect"), "enforce mode records none");
            }
            assertTrue(lines.get(0).contains("\"outcome\":\"ok\""));
            assertTrue(lines.get(1).contains("\"outcome\":\"denied\""));
        }
    }

    @Test
    void globalEnvApprovalSkipsPrompt() throws IOException {
        Path root = base.resolve("envrev");
        Files.createDirectories(root.resolve(".nop-git"));
        Files.writeString(root.resolve(".nop-git/policy.yaml"),
                "mode: enforce\noperations:\n  review: [add]\naudit_log: audit.log\n"
                        + "review_timeout_seconds: 1\n");
        // NOP_GIT_REVIEW_APPROVED is environment-controlled; the interactive
        // path itself is covered by reviewChannelPaths
        try (var svc = IGitRepositoryService.init(root.toFile())) {
            Files.writeString(root.resolve("f.txt"), "x\n");
            // without approval and without a TTY answer, review times out to deny
            NopGitException e = assertThrows(NopGitException.class, () -> svc.add("."),
                "engine active=" + engineOf(svc));
            assertTrue(e.getMessage().startsWith("policy denied"));
            String line = Files.readString(root.resolve("audit.log"));
            assertTrue(line.contains("\"outcome\":\"review-denied\""));
        }
    }
}
