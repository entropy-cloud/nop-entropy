/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.netty.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.logging.LogLevel;

public class NettyBaseConfig {

    private int readIdleTimeout;
    private int writeIdleTimeout;

    private boolean useSsl;
    private boolean useEpoll;
    private int workerGroupSize = 5;

    private int responseQueueSize = -1;

    /**
     * netty数据帧的最大长度。
     */
    private int aggMaxMessageSize = 1014 * 1024;

    private boolean tcpNoDelay = true;
    private boolean tcpKeepAlive = true;
    private boolean tcpReuseAddress = true;

    // 服务器端线程都处于busy状态时(线程池已满)，还可接受的连接数，即tcp的完全连接队列的大小
    private int tcpAcceptBacklog = 1024; // Nginx default 511, Tomcat default 100, java default 50
    private LogLevel logLevel;

    private int ioRatio = 50;
    private int writeBufferHighWaterMark;
    private int writeBufferLowWaterMark;

    private boolean useChannelGroup;

    private boolean channelCloseOnError = true;

    private int taskThreadCount = -1;

    private int maxPendingTasks = 4096;

    private TrafficShapingConfig globalTrafficShapingConfig;

    private TrafficShapingConfig channelTrafficShapingConfig;

    private int allowedEncodeErrorCount;
    private int allowedDecodeErrorCount;

    private SslConfig ssl;

    /**
     * 主动测试tcp粘包处理逻辑
     */
    private boolean testFragment;

    public int getReadIdleTimeout() {
        return readIdleTimeout;
    }

    public void setReadIdleTimeout(int readIdleTimeout) {
        this.readIdleTimeout = readIdleTimeout;
    }

    public int getWriteIdleTimeout() {
        return writeIdleTimeout;
    }

    public void setWriteIdleTimeout(int writeIdleTimeout) {
        this.writeIdleTimeout = writeIdleTimeout;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public boolean isUseEpoll() {
        return useEpoll;
    }

    public void setUseEpoll(boolean useEpoll) {
        this.useEpoll = useEpoll;
    }

    public int getWorkerGroupSize() {
        return workerGroupSize;
    }

    public void setWorkerGroupSize(int workerGroupSize) {
        this.workerGroupSize = workerGroupSize;
    }

    public int getResponseQueueSize() {
        return responseQueueSize;
    }

    public void setResponseQueueSize(int responseQueueSize) {
        this.responseQueueSize = responseQueueSize;
    }

    public int getAggMaxMessageSize() {
        return aggMaxMessageSize;
    }

    public void setAggMaxMessageSize(int aggMaxMessageSize) {
        this.aggMaxMessageSize = aggMaxMessageSize;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isTcpKeepAlive() {
        return tcpKeepAlive;
    }

    public void setTcpKeepAlive(boolean tcpKeepAlive) {
        this.tcpKeepAlive = tcpKeepAlive;
    }

    public boolean isTcpReuseAddress() {
        return tcpReuseAddress;
    }

    public void setTcpReuseAddress(boolean tcpReuseAddress) {
        this.tcpReuseAddress = tcpReuseAddress;
    }

    public int getTcpAcceptBacklog() {
        return tcpAcceptBacklog;
    }

    public void setTcpAcceptBacklog(int tcpAcceptBacklog) {
        this.tcpAcceptBacklog = tcpAcceptBacklog;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public int getIoRatio() {
        return ioRatio;
    }

    public void setIoRatio(int ioRatio) {
        this.ioRatio = ioRatio;
    }

    public int getWriteBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
    }

    public int getWriteBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
    }

    @JsonIgnore
    public WriteBufferWaterMark getWriteBufferWaterMark() {
        int high = this.writeBufferHighWaterMark;
        if (high <= 0)
            high = WriteBufferWaterMark.DEFAULT.high();
        int low = this.writeBufferLowWaterMark;
        if (low <= 0)
            low = WriteBufferWaterMark.DEFAULT.low();
        return new WriteBufferWaterMark(low, high);
    }

    public boolean isUseChannelGroup() {
        return useChannelGroup;
    }

    public void setUseChannelGroup(boolean useChannelGroup) {
        this.useChannelGroup = useChannelGroup;
    }

    public boolean isChannelCloseOnError() {
        return channelCloseOnError;
    }

    public void setChannelCloseOnError(boolean channelCloseOnError) {
        this.channelCloseOnError = channelCloseOnError;
    }

    public int getTaskThreadCount() {
        return taskThreadCount;
    }

    public void setTaskThreadCount(int taskThreadCount) {
        this.taskThreadCount = taskThreadCount;
    }

    public int getMaxPendingTasks() {
        return maxPendingTasks;
    }

    public void setMaxPendingTasks(int maxPendingTasks) {
        this.maxPendingTasks = maxPendingTasks;
    }

    public TrafficShapingConfig getGlobalTrafficShapingConfig() {
        return globalTrafficShapingConfig;
    }

    public void setGlobalTrafficShapingConfig(TrafficShapingConfig globalTrafficShapingConfig) {
        this.globalTrafficShapingConfig = globalTrafficShapingConfig;
    }

    public TrafficShapingConfig getChannelTrafficShapingConfig() {
        return channelTrafficShapingConfig;
    }

    public void setChannelTrafficShapingConfig(TrafficShapingConfig channelTrafficShapingConfig) {
        this.channelTrafficShapingConfig = channelTrafficShapingConfig;
    }

    public int getAllowedEncodeErrorCount() {
        return allowedEncodeErrorCount;
    }

    public void setAllowedEncodeErrorCount(int allowedEncodeErrorCount) {
        this.allowedEncodeErrorCount = allowedEncodeErrorCount;
    }

    public int getAllowedDecodeErrorCount() {
        return allowedDecodeErrorCount;
    }

    public void setAllowedDecodeErrorCount(int allowedDecodeErrorCount) {
        this.allowedDecodeErrorCount = allowedDecodeErrorCount;
    }

    public SslConfig getSsl() {
        return ssl;
    }

    public void setSsl(SslConfig ssl) {
        this.ssl = ssl;
    }

    public boolean isTestFragment() {
        return testFragment;
    }

    public void setTestFragment(boolean testFragment) {
        this.testFragment = testFragment;
    }
}
