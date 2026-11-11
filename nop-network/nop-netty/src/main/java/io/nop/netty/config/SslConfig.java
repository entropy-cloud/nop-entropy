package io.nop.netty.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.ssl.SslContext;

public class SslConfig {
    private String keyStorePassword;
    private String trustStorePassword;

    private String trustStorePath;

    private String keyStorePath;

    private String keyStoreType;

    private String serverSslAlgorithm;

    private boolean needClientAuth;

    private String clientSslAlgorithm;

    /**
     * 4.3-C1-014（plan 2561 裁定 A）：惰性缓存字段 volatile——并发首装时其他线程的
     * 早退读（{@code getClientSslContext()}）对锁内发布可见。
     */
    private volatile SslContext clientSslContext;
    private volatile SslContext serverSslContext;

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getServerSslAlgorithm() {
        return serverSslAlgorithm;
    }

    public void setServerSslAlgorithm(String serverSslAlgorithm) {
        this.serverSslAlgorithm = serverSslAlgorithm;
    }

    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    public String getClientSslAlgorithm() {
        return clientSslAlgorithm;
    }

    public void setClientSslAlgorithm(String clientSslAlgorithm) {
        this.clientSslAlgorithm = clientSslAlgorithm;
    }

    @JsonIgnore
    public SslContext getClientSslContext() {
        return clientSslContext;
    }

    public void setClientSslContext(SslContext clientSslContext) {
        this.clientSslContext = clientSslContext;
    }

    @JsonIgnore
    public SslContext getServerSslContext() {
        return serverSslContext;
    }

    public void setServerSslContext(SslContext serverSslContext) {
        this.serverSslContext = serverSslContext;
    }
}
