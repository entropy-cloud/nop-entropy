package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

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
}
