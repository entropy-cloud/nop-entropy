package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 200 focused tests for {@link DefaultPermissionMatrix}:
 * verifies the §5.3 channel × level matrix with usability-safe null channel.
 */
public class TestDefaultPermissionMatrix {

    private final DefaultPermissionMatrix matrix = new DefaultPermissionMatrix();
    private final Principal user = Principal.user();
    private final Principal operator = Principal.operator();

    // STANDARD allowed on all channels
    @Test
    void standardAllowedOnAllChannelsForUser() {
        for (ChannelKind channel : ChannelKind.values()) {
            assertTrue(matrix.check(channel, user, SecurityLevel.STANDARD).isAllowed(),
                    "STANDARD must be allowed on " + channel);
        }
        assertTrue(matrix.check(null, user, SecurityLevel.STANDARD).isAllowed(),
                "STANDARD must be allowed on null channel");
    }

    // ELEVATED: allowed on WEBUI/API/DM, denied on GROUP, allowed on null
    @Test
    void elevatedAllowedExceptGroup() {
        assertTrue(matrix.check(ChannelKind.WEBUI, user, SecurityLevel.ELEVATED).isAllowed());
        assertTrue(matrix.check(ChannelKind.API, user, SecurityLevel.ELEVATED).isAllowed());
        assertTrue(matrix.check(ChannelKind.DM, user, SecurityLevel.ELEVATED).isAllowed());
        assertTrue(matrix.check(null, user, SecurityLevel.ELEVATED).isAllowed(),
                "null channel allows ELEVATED (usability-safe)");
        assertFalse(matrix.check(ChannelKind.GROUP, user, SecurityLevel.ELEVATED).isAllowed(),
                "GROUP denies ELEVATED");
    }

    // RESTRICTED: allowed on WEBUI, denied on API/DM/GROUP/null for USER
    @Test
    void restrictedDeniedExceptWebuiForUser() {
        assertTrue(matrix.check(ChannelKind.WEBUI, user, SecurityLevel.RESTRICTED).isAllowed(),
                "WEBUI allows RESTRICTED");
        assertFalse(matrix.check(ChannelKind.API, user, SecurityLevel.RESTRICTED).isAllowed());
        assertFalse(matrix.check(ChannelKind.DM, user, SecurityLevel.RESTRICTED).isAllowed());
        assertFalse(matrix.check(ChannelKind.GROUP, user, SecurityLevel.RESTRICTED).isAllowed());
        assertFalse(matrix.check(null, user, SecurityLevel.RESTRICTED).isAllowed(),
                "null channel denies RESTRICTED (fail-closed for RESTRICTED)");
    }

    // OPERATOR bypasses RESTRICTED
    @Test
    void operatorBypassesRestricted() {
        for (ChannelKind channel : ChannelKind.values()) {
            assertTrue(matrix.check(channel, operator, SecurityLevel.RESTRICTED).isAllowed(),
                    "OPERATOR must bypass RESTRICTED on " + channel);
        }
        assertTrue(matrix.check(null, operator, SecurityLevel.RESTRICTED).isAllowed(),
                "OPERATOR must bypass RESTRICTED on null channel");
    }

    // Denial carries reason and context
    @Test
    void denialCarriesReasonAndContext() {
        MatrixDecision d = matrix.check(ChannelKind.GROUP, user, SecurityLevel.RESTRICTED);
        assertTrue(d.isDenied());
        assertTrue(d.getReason() != null && !d.getReason().isEmpty(),
                "denial must carry a reason");
    }
}
