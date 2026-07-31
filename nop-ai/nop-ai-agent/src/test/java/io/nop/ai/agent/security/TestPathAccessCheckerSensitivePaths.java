package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA5.4-P2-3: injectable sensitive-path rules on {@link DefaultPathAccessChecker}.
 * Verifies that constructor-injected prefixes/filenames actually reach the
 * check path (wiring) and that the built-in defaults remain intact.
 */
public class TestPathAccessCheckerSensitivePaths {

    private static AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    @Test
    void injectedPrefixIsDenied() {
        DefaultPathAccessChecker checker = new DefaultPathAccessChecker(
                List.of("/opt/company-secrets/"), null);

        PathAccessResult result = checker.checkAccess("/opt/company-secrets/token.txt", newContext());
        assertFalse(result.isAllowed(), "injected prefix must be denied");
        assertEquals("sensitive_path_prefix", result.getMatchedRule());
        assertNotNull(result.getReason());
    }

    @Test
    void injectedFilenameIsDenied() {
        DefaultPathAccessChecker checker = new DefaultPathAccessChecker(
                null, Set.of("vault-token"));

        PathAccessResult result = checker.checkAccess("/var/data/vault-token", newContext());
        assertFalse(result.isAllowed(), "injected filename must be denied");
        assertEquals("sensitive_path_filename", result.getMatchedRule());
    }

    @Test
    void defaultCheckerDoesNotDenyInjectedPath() {
        DefaultPathAccessChecker checker = new DefaultPathAccessChecker();

        PathAccessResult result = checker.checkAccess("/opt/company-secrets/token.txt", newContext());
        assertTrue(result.isAllowed(), "default checker must not know injected rules");
    }

    @Test
    void builtinDefaultsStillApplyWithInjectedRules() {
        DefaultPathAccessChecker checker = new DefaultPathAccessChecker(
                List.of("/opt/company-secrets/"), Set.of("vault-token"));

        String home = System.getProperty("user.home").replace("\\", "/");
        PathAccessResult ssh = checker.checkAccess(home + "/.ssh/id_rsa", newContext());
        assertFalse(ssh.isAllowed(), "built-in ~/.ssh prefix must still be denied");
        assertEquals("sensitive_path_prefix", ssh.getMatchedRule());

        PathAccessResult env = checker.checkAccess("/app/.env", newContext());
        assertFalse(env.isAllowed(), "built-in .env rule must still be denied");
    }

    @Test
    void nullArgumentsAreIgnored() {
        DefaultPathAccessChecker checker = new DefaultPathAccessChecker(null, null);

        String home = System.getProperty("user.home").replace("\\", "/");
        PathAccessResult ssh = checker.checkAccess(home + "/.ssh/id_rsa", newContext());
        assertFalse(ssh.isAllowed(), "null injection args must leave defaults intact");
    }
}
