package io.nop.auth.api.bind;

import io.nop.api.core.annotations.data.DataBean;

import java.sql.Timestamp;

/**
 * A single channel binding for a platform user, returned by
 * {@link IChannelBindService#completeBinding(String, String, String)},
 * {@link IChannelBindService#findBinding(String, String)}, and
 * {@link IChannelBindService#listBindings(String)}.
 *
 * <p>This is the {@code nop-auth-api} view of a {@code NopAuthExtLogin}
 * row where {@code verified=true} and {@code delFlag=0}. The mapping is:
 *
 * <ul>
 *   <li>{@code bindingId} &larr; {@code NopAuthExtLogin.sid} (the row primary
 *       key; used as the handle for {@link IChannelBindService#unbind}).</li>
 *   <li>{@code channelType} &larr; the {@code channelType} string for the
 *       row's {@code loginType} int, as defined by
 *       {@code ChannelTypeCodes} (e.g. {@code "feishu"} for {@code 20}).</li>
 *   <li>{@code extId} &larr; {@code NopAuthExtLogin.extId}; the channel-side
 *       user identity (Feishu {@code open_id}, DingTalk {@code unionId},
 *       ...). Equivalent to {@code ChannelBinding.channelAddress} on the
 *       W2 read path — same column, two read facades.</li>
 *   <li>{@code platformUserId} &larr; {@code NopAuthExtLogin.userId}.</li>
 *   <li>{@code boundAt} &larr; {@code NopAuthExtLogin.createTime}.</li>
 * </ul>
 *
 * <p><b>Layering note</b>: lives in {@code nop-auth-api} (depends only on
 * {@code nop-api-core}); does not reference {@code nop-integration-api}
 * types.
 */
@DataBean
public class ChannelBindingInfo {

    private String bindingId;
    private String channelType;
    private String extId;
    private String platformUserId;
    private Timestamp boundAt;

    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getExtId() {
        return extId;
    }

    public void setExtId(String extId) {
        this.extId = extId;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
    }

    public Timestamp getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(Timestamp boundAt) {
        this.boundAt = boundAt;
    }
}
