package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ErrorClassification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2304 (WI22): OVERLOADED joins the gateway switchable set — gateway
 * candidates are cross-provider/cross-model-class, so a provider overload is
 * exactly the standard switch scenario (the opposite ruling from the
 * coordinator's account chain, where a same-provider key swap is meaningless).
 */
public class TestOverloadedSwitchable {

    @Test
    void overloadedIsSwitchable() {
        assertTrue(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.OVERLOADED),
                "provider overload must trigger cross-provider failover");
    }

    @Test
    void existingSetUnchanged() {
        assertTrue(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.QUOTA_EXCEEDED));
        assertTrue(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.AUTH_INVALID));
        assertTrue(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.RATE_LIMITED));
        assertTrue(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.TRANSIENT));
        assertFalse(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.NON_TRANSIENT));
        assertFalse(ChatServiceFailoverAdapter.isSwitchable(ErrorClassification.CACHE_STATE_LOST));
    }
}
