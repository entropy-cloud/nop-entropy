package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAllowAllPermissionProvider {

    @Test
    void testAlwaysAllows() {
        AllowAllPermissionProvider provider = new AllowAllPermissionProvider();
        Permission p = provider.resolve("any_tool", "any_agent", "any_session");
        assertTrue(p.isAllowed());
    }

    @Test
    void testNullArguments() {
        AllowAllPermissionProvider provider = new AllowAllPermissionProvider();
        Permission p = provider.resolve(null, null, null);
        assertTrue(p.isAllowed());
    }

    @Test
    void testMultipleCallsAlwaysAllow() {
        AllowAllPermissionProvider provider = new AllowAllPermissionProvider();
        for (int i = 0; i < 10; i++) {
            Permission p = provider.resolve("tool_" + i, "agent", "session");
            assertTrue(p.isAllowed());
        }
    }
}
