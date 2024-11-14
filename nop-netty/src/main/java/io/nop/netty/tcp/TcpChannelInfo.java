package io.nop.netty.tcp;

import io.netty.util.AttributeKey;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class TcpChannelInfo {
    public static AttributeKey<TcpChannelInfo> ATTR_KEY = AttributeKey.valueOf("channelInfo");
    private String channelId;
    private long connectTime;

    private String remoteAddress;

    private String localAddress;

    private volatile long sendBytes;
    private volatile long recvBytes;

    private volatile long sendCount;
    private volatile long recvCount;

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public long getConnectTime() {
        return connectTime;
    }

    public void setConnectTime(long connectTime) {
        this.connectTime = connectTime;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public long getSendBytes() {
        return sendBytes;
    }

    public void setSendBytes(long sendBytes) {
        this.sendBytes = sendBytes;
    }

    public long getRecvBytes() {
        return recvBytes;
    }

    public void setRecvBytes(long recvBytes) {
        this.recvBytes = recvBytes;
    }

    public long getSendCount() {
        return sendCount;
    }

    public void setSendCount(long sendCount) {
        this.sendCount = sendCount;
    }

    public long getRecvCount() {
        return recvCount;
    }

    public void setRecvCount(long recvCount) {
        this.recvCount = recvCount;
    }
}
