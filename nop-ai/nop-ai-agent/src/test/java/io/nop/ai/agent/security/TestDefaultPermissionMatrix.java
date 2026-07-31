package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plan 200 focused tests for {@link DefaultPermissionMatrix}:
 * verifies the §5.3 channel × level matrix with usability-safe null channel.
 * Value-level assertions (MA4.3-09 upgrade): allow paths compare against
 * {@link MatrixDecision#allow()} (all-field equals covers the allowed flag
 * plus the null reason/channel/level fields); deny paths assert the
 * structured channel + level context the denial carries for audit
 * categorization, not merely the boolean outcome.
 */
public class TestDefaultPermissionMatrix {

    private final DefaultPermissionMatrix matrix = new DefaultPermissionMatrix();
    private final Principal user = Principal.user();
    private final Principal operator = Principal.operator();

    // STANDARD allowed on all channels
    @Test
    void standardAllowedOnAllChannelsForUser() {
        for (ChannelKind channel : ChannelKind.values()) {
            assertEquals(MatrixDecision.allow(), matrix.check(channel, user, SecurityLevel.STANDARD),
                    "STANDARD must be allowed on " + channel);
        }
        assertEquals(MatrixDecision.allow(), matrix.check(null, user, SecurityLevel.STANDARD),
                "STANDARD must be allowed on null channel");
    }

    // ELEVATED: allowed on WEBUI/API/DM, denied on GROUP, allowed on null
    @Test
    void elevatedAllowedExceptGroup() {
        assertEquals(MatrixDecision.allow(), matrix.check(ChannelKind.WEBUI, user, SecurityLevel.ELEVATED));
        assertEquals(MatrixDecision.allow(), matrix.check(ChannelKind.API, user, SecurityLevel.ELEVATED));
        assertEquals(MatrixDecision.allow(), matrix.check(ChannelKind.DM, user, SecurityLevel.ELEVATED));
        assertEquals(MatrixDecision.allow(), matrix.check(null, user, SecurityLevel.ELEVATED),
                "null channel allows ELEVATED (usability-safe)");
        assertDeniedFor(matrix.check(ChannelKind.GROUP, user, SecurityLevel.ELEVATED),
                ChannelKind.GROUP, SecurityLevel.ELEVATED);
    }

    // RESTRICTED: allowed on WEBUI, denied on API/DM/GROUP/null for USER
    @Test
    void restrictedDeniedExceptWebuiForUser() {
        assertEquals(MatrixDecision.allow(), matrix.check(ChannelKind.WEBUI, user, SecurityLevel.RESTRICTED),
                "WEBUI allows RESTRICTED");
        assertDeniedFor(matrix.check(ChannelKind.API, user, SecurityLevel.RESTRICTED),
                ChannelKind.API, SecurityLevel.RESTRICTED);
        assertDeniedFor(matrix.check(ChannelKind.DM, user, SecurityLevel.RESTRICTED),
                ChannelKind.DM, SecurityLevel.RESTRICTED);
        assertDeniedFor(matrix.check(ChannelKind.GROUP, user, SecurityLevel.RESTRICTED),
                ChannelKind.GROUP, SecurityLevel.RESTRICTED);
        // fail-closed for RESTRICTED on null/unknown channels: the denial
        // carries no channel but the restricted level context.
        assertDeniedFor(matrix.check(null, user, SecurityLevel.RESTRICTED),
                null, SecurityLevel.RESTRICTED);
    }

    // OPERATOR bypasses RESTRICTED
    @Test
    void operatorBypassesRestricted() {
        for (ChannelKind channel : ChannelKind.values()) {
            assertEquals(MatrixDecision.allow(), matrix.check(channel, operator, SecurityLevel.RESTRICTED),
                    "OPERATOR must bypass RESTRICTED on " + channel);
        }
        assertEquals(MatrixDecision.allow(), matrix.check(null, operator, SecurityLevel.RESTRICTED),
                "OPERATOR must bypass RESTRICTED on null channel");
    }

    // Denial carries reason and structured channel/level context
    @Test
    void denialCarriesReasonAndContext() {
        assertDeniedFor(matrix.check(ChannelKind.GROUP, user, SecurityLevel.RESTRICTED),
                ChannelKind.GROUP, SecurityLevel.RESTRICTED);
    }

    /**
     * Value-level assertion of a deny decision: the matrix must return a
     * denial whose structured channel/level context identifies the exact
     * restriction that triggered it (audit categorization contract), with a
     * non-empty human-readable reason.
     */
    private void assertDeniedFor(MatrixDecision decision, ChannelKind expectedChannel,
                                 SecurityLevel expectedLevel) {
        assertFalse(decision.isAllowed(), "denial: isAllowed must be false");
        assertEquals(expectedChannel, decision.getChannel(),
                "denial carries the channel that triggered the restriction");
        assertEquals(expectedLevel, decision.getLevel(),
                "denial carries the level that triggered the restriction");
        assertNotNull(decision.getReason(), "denial must carry an auditable reason");
    }
}
