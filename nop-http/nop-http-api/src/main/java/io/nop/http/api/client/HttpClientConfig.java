/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.api.core.util.LogLevel;
import io.nop.http.api.IDnsResolver;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executor;

@ConfigBean
public class HttpClientConfig {
    private boolean http2;
    private int maxConnTotal = 2000;
    private int maxRequestsPerHost;

    private String sslVersion = "TLSv1.2";

    private int ioThreadCount;

    private boolean followRedirects;
    private boolean retryOnConnectionFailure;
    private Duration readTimeout = Duration.of(30, ChronoUnit.SECONDS);
    private Duration writeTimeout = Duration.of(30, ChronoUnit.SECONDS);
    private Duration connectTimeout = Duration.of(10, ChronoUnit.SECONDS);
    private Duration maxIdleTime = Duration.of(5, ChronoUnit.MINUTES);
    private int maxIdleCount = 5;

    private Duration keepAliveDuration;

    private LogLevel logLevel;
    private boolean useSsl;
    private boolean logBody;
    private String userAgent;
    private boolean prettyJson;

    private int maxResultLength;

    boolean ignoreSslCerts;
    private SSLSocketFactory sslSocketFactory;
    private X509TrustManager[] x509TrustManagers;
    private KeyManager[] keyManagers;
    private HostnameVerifier hostnameVerifier;
    private SecureRandom secureRandom;

    /**
     * proxy configurations
     */

    private String httpProxy = null;
    private String httpsProxy = null;
    private String noProxy = null;

    private IDnsResolver dnsResolver;

    private String threadName = "nop-http-client";
    private int threadPoolSize;
    private Executor executor;

    public int getIoThreadCount() {
        return ioThreadCount;
    }

    public void setIoThreadCount(int ioThreadCount) {
        this.ioThreadCount = ioThreadCount;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public boolean isHttp2() {
        return http2;
    }

    public void setHttp2(boolean http2) {
        this.http2 = http2;
    }

    public IDnsResolver getDnsResolver() {
        return dnsResolver;
    }

    public void setDnsResolver(IDnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(Duration maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public int getMaxIdleCount() {
        return maxIdleCount;
    }

    public void setMaxIdleCount(int maxIdleCount) {
        this.maxIdleCount = maxIdleCount;
    }

    public Duration getKeepAliveDuration() {
        return keepAliveDuration;
    }

    public void setKeepAliveDuration(Duration keepAliveDuration) {
        this.keepAliveDuration = keepAliveDuration;
    }

    public boolean isPrettyJson() {
        return prettyJson;
    }

    public void setPrettyJson(boolean prettyJson) {
        this.prettyJson = prettyJson;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSslVersion() {
        return sslVersion;
    }

    public void setSslVersion(String sslVersion) {
        this.sslVersion = sslVersion;
    }

    public boolean isLogBody() {
        return logBody;
    }

    public void setLogBody(boolean logBody) {
        this.logBody = logBody;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public int getMaxConnTotal() {
        return maxConnTotal;
    }

    public void setMaxConnTotal(int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public boolean isRetryOnConnectionFailure() {
        return retryOnConnectionFailure;
    }

    public void setRetryOnConnectionFailure(boolean retryOnConnectionFailure) {
        this.retryOnConnectionFailure = retryOnConnectionFailure;
    }


    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public int getMaxRequestsPerHost() {
        return maxRequestsPerHost;
    }

    public void setMaxRequestsPerHost(int maxRequestsPerHost) {
        this.maxRequestsPerHost = maxRequestsPerHost;
    }

    public int getMaxResultLength() {
        return maxResultLength;
    }

    public void setMaxResultLength(int maxResultLength) {
        this.maxResultLength = maxResultLength;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    public void setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public X509TrustManager[] getX509TrustManagers() {
        return x509TrustManagers;
    }

    public void setX509TrustManagers(X509TrustManager[] x509TrustManagers) {
        this.x509TrustManagers = x509TrustManagers;
    }

    public KeyManager[] getKeyManagers() {
        return keyManagers;
    }

    public void setKeyManagers(KeyManager[] keyManagers) {
        this.keyManagers = keyManagers;
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public boolean isIgnoreSslCerts() {
        return ignoreSslCerts;
    }

    public void setIgnoreSslCerts(boolean ignoreSslCerts) {
        this.ignoreSslCerts = ignoreSslCerts;
    }

    public SecureRandom getSecureRandom() {
        return secureRandom;
    }

    public void setSecureRandom(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    public void setHttpProxy(String httpProxy) {
        this.httpProxy = httpProxy;
    }

    public String getHttpsProxy() {
        return httpsProxy;
    }

    public void setHttpsProxy(String httpsProxy) {
        this.httpsProxy = httpsProxy;
    }

    public String getNoProxy() {
        return noProxy;
    }

    public void setNoProxy(String noProxy) {
        this.noProxy = noProxy;
    }
}
