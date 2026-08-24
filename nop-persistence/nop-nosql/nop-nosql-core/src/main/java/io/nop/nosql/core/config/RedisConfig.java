/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.core.config;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class RedisConfig {
    private String host = "localhost";
    private int port = 6379;
    private int connectionTimeout = 2000;
    private int soTimeout = 2000;
    private boolean useSsl = false;

    /**
     * useSsl 开启时是否校验服务端证书。默认 true；仅在显式接受自签证书等场景下才应设置为 false，
     * 关闭校验会使 TLS 连接失去对端身份保证（可被中间人攻击）。
     */
    private boolean verifyPeer = true;
    private String username;
    private String password;

    private List<String> clusterNodes;
    private int maxRedirections = 5;

    private int database = 0;

    /**
     * 哨兵模式的 sentinel 节点地址列表（"host:port" 或 "[ipv6-host]:port"）。
     * 配置后配合 {@link #masterName} 启用 sentinel 拓扑；仅配置 masterName 而不配置
     * sentinelNodes 无法构成有效的哨兵连接。
     */
    private List<String> sentinelNodes;
    private String masterName;
    private String clientName;

    /**
     * 因为是异步处理，一般情况下和redis之间只需要建立一个连接。如果压力加大，可以增加连接个数。
     */
    private int connectionPoolSize;

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public boolean isVerifyPeer() {
        return verifyPeer;
    }

    public void setVerifyPeer(boolean verifyPeer) {
        this.verifyPeer = verifyPeer;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getClusterNodes() {
        return clusterNodes;
    }

    public void setClusterNodes(List<String> clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public List<String> getSentinelNodes() {
        return sentinelNodes;
    }

    public void setSentinelNodes(List<String> sentinelNodes) {
        this.sentinelNodes = sentinelNodes;
    }

    public int getMaxRedirections() {
        return maxRedirections;
    }

    public void setMaxRedirections(int maxRedirections) {
        this.maxRedirections = maxRedirections;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public String getMasterName() {
        return masterName;
    }

    public void setMasterName(String masterName) {
        this.masterName = masterName;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
}
