package io.nop.integration.api.channel;

import io.nop.api.core.annotations.data.DataBean;

/**
 * A single resolved channel binding for a platform user: the user has bound
 * the external-channel identity {@code channelAddress} (e.g. a Feishu
 * {@code open_id}) on channel {@code channelType}. Produced by
 * {@link UserChannelResolver} when translating {@code nop_auth_ext_login}
 * rows into channel-agnostic binding descriptors.
 */
@DataBean
public class ChannelBinding {

    private String userId;
    private String channelType;
    private String channelAddress;

    public ChannelBinding() {
    }

    public ChannelBinding(String userId, String channelType, String channelAddress) {
        this.userId = userId;
        this.channelType = channelType;
        this.channelAddress = channelAddress;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getChannelAddress() {
        return channelAddress;
    }

    public void setChannelAddress(String channelAddress) {
        this.channelAddress = channelAddress;
    }
}
