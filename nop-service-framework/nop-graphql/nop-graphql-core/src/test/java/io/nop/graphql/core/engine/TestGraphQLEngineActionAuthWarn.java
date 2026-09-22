package io.nop.graphql.core.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-API2-1 回归：enableActionAuth=true 但 checker bean 缺席时必须触发
 * 显著告警（标志位观测；修复前静默 fail-open 零可观测）。
 */
public class TestGraphQLEngineActionAuthWarn {

    @Test
    public void testWarnFlagFiresWhenEnabledWithoutChecker() {
        GraphQLEngine engine = new GraphQLEngine();
        engine.setEnableActionAuth(true);
        engine.setActionAuthChecker(null);
        assertTrue(engine.actionAuthNoCheckerWarned,
                "warn must fire when action-auth is enabled but no checker bean is present");
    }

    @Test
    public void testNoWarnWhenActionAuthDisabled() {
        GraphQLEngine engine = new GraphQLEngine();
        engine.setEnableActionAuth(false);
        engine.setActionAuthChecker(null);
        assertFalse(engine.actionAuthNoCheckerWarned,
                "no warn noise when action-auth is not enabled (optional injection semantics preserved)");
    }
}
