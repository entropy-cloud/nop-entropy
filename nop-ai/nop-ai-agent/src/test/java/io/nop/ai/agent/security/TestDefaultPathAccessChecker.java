package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultPathAccessChecker {

    private final DefaultPathAccessChecker checker = new DefaultPathAccessChecker();

    private AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    @Test
    void testSshKeyDenied() {
        String home = System.getProperty("user.home");
        PathAccessResult result = checker.checkAccess(home + "/.ssh/id_rsa", newContext());
        assertFalse(result.isAllowed());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains(home + "/.ssh/id_rsa"));
        assertEquals("sensitive_path_prefix", result.getMatchedRule());
    }

    @Test
    void testAwsCredentialsDenied() {
        String home = System.getProperty("user.home");
        PathAccessResult result = checker.checkAccess(home + "/.aws/credentials", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testAzureCredentialsDenied() {
        String home = System.getProperty("user.home");
        PathAccessResult result = checker.checkAccess(home + "/.azure/azure_app.json", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testKubeConfigDenied() {
        String home = System.getProperty("user.home");
        PathAccessResult result = checker.checkAccess(home + "/.kube/config", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testEnvFileDenied() {
        PathAccessResult result = checker.checkAccess("/app/.env", newContext());
        assertFalse(result.isAllowed());
        assertEquals("sensitive_path_env_file", result.getMatchedRule());
    }

    @Test
    void testEnvVariantFileDenied() {
        PathAccessResult result = checker.checkAccess("/app/.env.production", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testNetrcDenied() {
        PathAccessResult result = checker.checkAccess("/home/user/.netrc", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testBashHistoryDenied() {
        PathAccessResult result = checker.checkAccess("/home/user/.bash_history", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testEtcDirectoryDenied() {
        PathAccessResult result = checker.checkAccess("/etc/passwd", newContext());
        assertFalse(result.isAllowed());
        assertEquals("sensitive_path_prefix", result.getMatchedRule());
    }

    @Test
    void testRootDirectoryDenied() {
        PathAccessResult result = checker.checkAccess("/root/.bashrc", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testNormalPathAllowed() {
        PathAccessResult result = checker.checkAccess("/tmp/workdir/output.txt", newContext());
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getMatchedRule());
    }

    @Test
    void testWorkspacePathAllowed() {
        PathAccessResult result = checker.checkAccess("/home/user/project/src/Main.java", newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testRelativePathAllowed() {
        PathAccessResult result = checker.checkAccess("src/main/java/App.java", newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testPathTraversalBlocked() {
        PathAccessResult result = checker.checkAccess("../../etc/passwd", newContext());
        assertFalse(result.isAllowed());
        assertEquals("path_traversal_defense", result.getMatchedRule());
    }

    @Test
    void testPathTraversalWithEncodeBlocked() {
        PathAccessResult result = checker.checkAccess("app/../../../etc/shadow", newContext());
        assertFalse(result.isAllowed());
        assertEquals("path_traversal_defense", result.getMatchedRule());
    }

    @Test
    void testTildeExpansionSshKey() {
        PathAccessResult result = checker.checkAccess("~/.ssh/config", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testTildeExpansionAws() {
        PathAccessResult result = checker.checkAccess("~/.aws/credentials", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testNullPathAllowed() {
        PathAccessResult result = checker.checkAccess(null, newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testEmptyPathAllowed() {
        PathAccessResult result = checker.checkAccess("", newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testBlankPathAllowed() {
        PathAccessResult result = checker.checkAccess("   ", newContext());
        assertTrue(result.isAllowed());
    }

    @Test
    void testCaseInsensitiveSensitivePath() {
        PathAccessResult result = checker.checkAccess("/ETC/passwd", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testWindowsBackslashPath() {
        PathAccessResult result = checker.checkAccess("C:\\Users\\admin\\.ssh\\id_rsa", newContext());
        assertFalse(result.isAllowed());
    }

    @Test
    void testAllowAllPathAccessChecker() {
        AllowAllPathAccessChecker allowAll = new AllowAllPathAccessChecker();
        PathAccessResult result = allowAll.checkAccess("/etc/passwd", newContext());
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
        assertNull(result.getMatchedRule());
    }

    @Test
    void testAllowAllPathAccessCheckerNullPath() {
        AllowAllPathAccessChecker allowAll = new AllowAllPathAccessChecker();
        PathAccessResult result = allowAll.checkAccess(null, newContext());
        assertTrue(result.isAllowed());
    }

    // ========================================================================
    // Plan 270 finding 13-1: symlink protection (toRealPath resolution)
    // ========================================================================

    /**
     * End-to-end (Minimum Rules #22): a symlink whose lexical name is
     * innocuous but whose target is a sensitive directory must be denied.
     *
     * <p>{@code tempDir/link -> ~/.ssh}; accessing {@code tempDir/link/config}
     * lexically looks safe (filename {@code config}, no sensitive prefix)
     * but the real path resolves to {@code ~/.ssh/config} which matches the
     * {@code HOME/.ssh/} sensitive prefix. Skips (does not fail) when no
     * sensitive target dir exists in the user's home, mirroring the
     * Docker integration test's skip-on-missing-prerequisite convention.
     */
    @Test
    void symlinkToSensitiveDirIsDenied(@TempDir Path tempDir) throws Exception {
        String home = System.getProperty("user.home", "");
        Path sensitiveTarget = null;
        if (!home.isEmpty()) {
            Path ssh = Path.of(home, ".ssh");
            if (Files.isDirectory(ssh)) {
                sensitiveTarget = ssh;
            } else {
                Path aws = Path.of(home, ".aws");
                if (Files.isDirectory(aws)) {
                    sensitiveTarget = aws;
                }
            }
        }
        if (sensitiveTarget == null) {
            // No existing sensitive home dir to point a symlink at on this
            // host — the resolution + IOException-subclass tests below still
            // prove the mechanism. Skip rather than fail.
            return;
        }

        Path link = tempDir.resolve("looks-safe-link");
        Files.createSymbolicLink(link, sensitiveTarget);
        // Access a file THROUGH the link so the lexical path looks safe but
        // the real path lands inside the sensitive dir.
        PathAccessResult result = checker.checkAccess(
                link.resolve("config").toString(), newContext());

        assertFalse(result.isAllowed(),
                "symlink to a sensitive dir must be denied; real path would resolve into "
                        + sensitiveTarget + ". Got reason: " + result.getReason());
        assertNotNull(result.getMatchedRule(),
                "a symlink-sensitive denial must carry a matched rule");
    }

    /**
     * Wiring Verification (#23) + No-Silent-No-Op (#24): when symlink real-path
     * resolution fails (i.e. {@code toRealPath()} throws {@code IOException}),
     * the checker must DENY — never silently allow a path whose true target
     * is unknown.
     *
     * <p>Overrides {@code resolveSymlinkRealPath} to simulate the
     * {@code IOException} path (returning {@code null}) without a mocking
     * framework, then asserts a non-sensitive path is denied. This proves
     * {@code toRealPath()} is consulted inside {@code checkAccess} (the
     * overridden hook is the only thing that changed) and that its failure
     * is fail-closed.
     */
    @Test
    void symlinkResolutionFailureDeniesAccess() {
        DefaultPathAccessChecker failingResolution = new DefaultPathAccessChecker() {
            @Override
            protected String resolveSymlinkRealPath(String normalized) {
                return null;
            }
        };
        // A path that passes all lexical checks (not sensitive, no traversal)
        // so the only thing that can deny it is the symlink-resolution hook.
        PathAccessResult result = failingResolution.checkAccess(
                "/tmp/some-ordinary-path/file.txt", newContext());

        assertFalse(result.isAllowed(),
                "when toRealPath() throws IOException, access must be denied, not silently allowed");
        assertNotNull(result.getReason());
    }

    /**
     * Regression guard: a legitimate, non-sensitive existing path must still
     * be allowed after the symlink-resolution layer is added (the layer must
     * not over-deny normal access). The path resolves through a real temp dir
     * ancestor, proving the existing-ancestor fallback works for normal paths.
     */
    @Test
    void symlinkLayerAllowsLegitimateExistingPath(@TempDir Path tempDir) throws Exception {
        Path nested = tempDir.resolve("project/src/Main.java");
        Files.createDirectories(nested.getParent());

        PathAccessResult result = checker.checkAccess(nested.toString(), newContext());
        assertTrue(result.isAllowed(),
                "a legitimate non-sensitive path must still be allowed; got reason: "
                        + result.getReason());

        // And a brand-new (non-existent) file inside a legit dir is also allowed.
        PathAccessResult newFile = checker.checkAccess(
                tempDir.resolve("brand-new-output.txt").toString(), newContext());
        assertTrue(newFile.isAllowed(),
                "a non-existent file inside a legitimate dir must be allowed; got reason: "
                        + newFile.getReason());
    }
}
