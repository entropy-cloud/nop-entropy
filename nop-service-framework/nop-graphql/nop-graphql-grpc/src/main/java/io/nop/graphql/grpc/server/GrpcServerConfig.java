/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc.server;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;

import java.io.File;
import java.time.Duration;

@DataBean
public class GrpcServerConfig {
    private int port;

    private ThreadPoolConfig threadPool;

    /**
     * 使用TLS
     */
    private File certChain;

    private File privateKey;

    private Duration handshakeTimeout;

    private Duration keepAliveTimeout;

    private Duration maxConnectionIdle;

    private Duration maxConnectionAge;

    private Duration maxConnectionArgGrace;

    private Duration permitKeepAliveTime;

    private Boolean permitKeepAliveWithoutCalls;

    private int maxInboundMessageSize;

    private int maxInboundMetadataSize;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ThreadPoolConfig getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPoolConfig threadPool) {
        this.threadPool = threadPool;
    }

    public File getCertChain() {
        return certChain;
    }

    public void setCertChain(File certChain) {
        this.certChain = certChain;
    }

    public File getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(File privateKey) {
        this.privateKey = privateKey;
    }

    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    public void setHandshakeTimeout(Duration handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
    }

    public Duration getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    public void setKeepAliveTimeout(Duration keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public Duration getMaxConnectionIdle() {
        return maxConnectionIdle;
    }

    public void setMaxConnectionIdle(Duration maxConnectionIdle) {
        this.maxConnectionIdle = maxConnectionIdle;
    }

    public Duration getMaxConnectionAge() {
        return maxConnectionAge;
    }

    public void setMaxConnectionAge(Duration maxConnectionAge) {
        this.maxConnectionAge = maxConnectionAge;
    }

    public Duration getMaxConnectionArgGrace() {
        return maxConnectionArgGrace;
    }

    public void setMaxConnectionArgGrace(Duration maxConnectionArgGrace) {
        this.maxConnectionArgGrace = maxConnectionArgGrace;
    }

    public Duration getPermitKeepAliveTime() {
        return permitKeepAliveTime;
    }

    public void setPermitKeepAliveTime(Duration permitKeepAliveTime) {
        this.permitKeepAliveTime = permitKeepAliveTime;
    }

    public Boolean getPermitKeepAliveWithoutCalls() {
        return permitKeepAliveWithoutCalls;
    }

    public void setPermitKeepAliveWithoutCalls(Boolean permitKeepAliveWithoutCalls) {
        this.permitKeepAliveWithoutCalls = permitKeepAliveWithoutCalls;
    }

    public int getMaxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    public void setMaxInboundMessageSize(int maxInboundMessageSize) {
        this.maxInboundMessageSize = maxInboundMessageSize;
    }

    public int getMaxInboundMetadataSize() {
        return maxInboundMetadataSize;
    }

    public void setMaxInboundMetadataSize(int maxInboundMetadataSize) {
        this.maxInboundMetadataSize = maxInboundMetadataSize;
    }
}
