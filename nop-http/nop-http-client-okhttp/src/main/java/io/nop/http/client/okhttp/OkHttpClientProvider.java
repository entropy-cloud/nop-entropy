/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.client.okhttp;

import io.nop.api.core.util.LogLevel;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.IHttpClient;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OkHttpClientProvider extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(OkHttpClientProvider.class);

    private HttpClientConfig config;
    private List<Interceptor> interceptors;

    private OkHttpClientImpl client;

    private HostnameVerifier hostnameVerifier;
    private X509TrustManager trustManager;

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public X509TrustManager getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public void setConfig(HttpClientConfig config) {
        this.config = config;
    }

    public HttpClientConfig getConfig() {
        return config;
    }

    public void setInterceptors(List<Interceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public IHttpClient getClient() {
        return client;
    }

    @Override
    protected void doStart() {
        OkHttpClient okHttpClient = newBuilder().build();
        this.client = new OkHttpClientImpl(okHttpClient, config);
    }

    protected OkHttpClient.Builder newBuilder() {
        ConnectionPool pool = null;

        if (config.getMaxIdleTime() != null && config.getMaxIdleCount() > 0) {
            pool = new ConnectionPool(config.getMaxIdleCount(), config.getMaxIdleTime().get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        Dispatcher dispatcher = new Dispatcher();

        if (config.getMaxRequestsPerHost() > 0) {
            dispatcher.setMaxRequestsPerHost(config.getMaxRequestsPerHost());
        }

        if (config.getMaxConnTotal() > 0) {
            dispatcher.setMaxRequests(config.getMaxConnTotal());
        }

        builder.dispatcher(dispatcher);
        addSSLConfig(builder);


        if (config.getConnectTimeout() != null) {
            builder.connectTimeout(config.getConnectTimeout());
        }
        builder.retryOnConnectionFailure(config.isRetryOnConnectionFailure())
                .followRedirects(config.isFollowRedirects());
        if (pool != null) {
            builder.connectionPool(pool);
        }
        if (config.getReadTimeout() != null) {
            builder.readTimeout(config.getReadTimeout());
        }

        if (config.getWriteTimeout() != null) {
            builder.writeTimeout(config.getWriteTimeout());
        }

        if (config.getLogLevel() != null) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    LogLevel.log(LOG, config.getLogLevel(), message);
                }
            });

            if (config.isLogBody()) {
                interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            } else {
                interceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            }
            builder.addInterceptor(interceptor);
        }

        if (interceptors != null) {
            for (Interceptor interceptor : interceptors) {
                builder.addInterceptor(interceptor);
            }
        }

        return builder;
    }

    protected void addSSLConfig(OkHttpClient.Builder builder) {
        if (config.isUseSsl()) {
            try {
                X509TrustManager trustManager = this.trustManager != null ? this.trustManager
                        : new DisableValidationTrustManager();
                TrustManager[] trustManagers = new TrustManager[]{trustManager};
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init((KeyManager[]) null, trustManagers, new SecureRandom());
                SSLSocketFactory socketFactory = sslContext.getSocketFactory();
                builder.sslSocketFactory(socketFactory, trustManager);

                HostnameVerifier verifier = this.hostnameVerifier != null ? this.hostnameVerifier
                        : new TrustAllHostnames();
                builder.hostnameVerifier(verifier);
            } catch (Exception e) {
                LOG.warn("nop.err.okhttp.setSocketFactory-fail", e);
            }
        }
    }

    @Override
    protected void doStop() {
        if (client != null) {
            client.stop();
        }
    }

    public static class TrustAllHostnames implements HostnameVerifier {
        public TrustAllHostnames() {
        }

        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    public static class DisableValidationTrustManager implements X509TrustManager {
        public DisableValidationTrustManager() {
        }

        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
