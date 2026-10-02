package io.nop.integration.api.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: the authoritative loginType int <-> channelType
 * string mapping must stay a closed bijection over the four channel codes,
 * and non-channel login types (password / SSO / unknown) must map to null /
 * -1 with no silent default (resolver callers treat null as "skip").
 */
public class TestChannelTypeCodes {

    @Test
    public void testForwardMapping() {
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_FEISHU,
                ChannelTypeCodes.channelType(ChannelTypeCodes.LOGIN_TYPE_FEISHU));
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_DINGTALK,
                ChannelTypeCodes.channelType(ChannelTypeCodes.LOGIN_TYPE_DINGTALK));
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_WECOM,
                ChannelTypeCodes.channelType(ChannelTypeCodes.LOGIN_TYPE_WECOM));
        assertEquals(ChannelTypeCodes.CHANNEL_TYPE_WEBHOOK,
                ChannelTypeCodes.channelType(ChannelTypeCodes.LOGIN_TYPE_WEBHOOK));
    }

    @Test
    public void testInverseMapping() {
        assertEquals(ChannelTypeCodes.LOGIN_TYPE_FEISHU,
                ChannelTypeCodes.loginType(ChannelTypeCodes.CHANNEL_TYPE_FEISHU));
        assertEquals(ChannelTypeCodes.LOGIN_TYPE_DINGTALK,
                ChannelTypeCodes.loginType(ChannelTypeCodes.CHANNEL_TYPE_DINGTALK));
        assertEquals(ChannelTypeCodes.LOGIN_TYPE_WECOM,
                ChannelTypeCodes.loginType(ChannelTypeCodes.CHANNEL_TYPE_WECOM));
        assertEquals(ChannelTypeCodes.LOGIN_TYPE_WEBHOOK,
                ChannelTypeCodes.loginType(ChannelTypeCodes.CHANNEL_TYPE_WEBHOOK));
    }

    @Test
    public void testRoundTripIsBijective() {
        int[] loginTypes = {
                ChannelTypeCodes.LOGIN_TYPE_FEISHU,
                ChannelTypeCodes.LOGIN_TYPE_DINGTALK,
                ChannelTypeCodes.LOGIN_TYPE_WECOM,
                ChannelTypeCodes.LOGIN_TYPE_WEBHOOK};
        for (int loginType : loginTypes) {
            String channelType = ChannelTypeCodes.channelType(loginType);
            assertTrue(ChannelTypeCodes.isChannelLoginType(loginType),
                    "channel login types must be recognized: " + loginType);
            assertEquals(loginType, ChannelTypeCodes.loginType(channelType),
                    "forward/inverse mapping must round-trip: " + loginType);
        }
    }

    @Test
    public void testNonChannelAndUnknownLoginTypesHaveNoChannelType() {
        assertNull(ChannelTypeCodes.channelType(1), "password login is not a channel binding");
        assertNull(ChannelTypeCodes.channelType(10), "SSO login is not a channel binding");
        assertNull(ChannelTypeCodes.channelType(99), "unknown codes map to null");
        assertFalse(ChannelTypeCodes.isChannelLoginType(1));
        assertFalse(ChannelTypeCodes.isChannelLoginType(0));
        assertEquals(-1, ChannelTypeCodes.loginType(null), "null channelType maps to -1, never NPE");
        assertEquals(-1, ChannelTypeCodes.loginType("unknown"), "unknown channelType maps to -1");
    }
}
