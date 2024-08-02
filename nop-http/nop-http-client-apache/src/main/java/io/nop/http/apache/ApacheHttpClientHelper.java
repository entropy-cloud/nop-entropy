package io.nop.http.apache;

import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.support.CompositeX509TrustManager;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.nop.http.api.HttpApiErrors.ERR_HTTP_INIT_SSL_FAIL;

public class ApacheHttpClientHelper {

    public static CloseableHttpAsyncClient createHttp2(HttpClientConfig clientConfig, IOReactorConfig ioReactorConfig) {
        H2AsyncClientBuilder builder = HttpAsyncClients.customHttp2();
        builder.setIOReactorConfig(ioReactorConfig)
                .evictIdleConnections(TimeValue.ofMilliseconds(clientConfig.getMaxIdleTime().toMillis()))
                .setUserAgent(clientConfig.getUserAgent()).build();
        return builder.build();
    }

    public static CloseableHttpAsyncClient createHttp(HttpClientConfig clientConfig, IOReactorConfig ioReactorConfig,
                                                      PoolingAsyncClientConnectionManager connManager) {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();
        builder.setIOReactorConfig(ioReactorConfig).setConnectionManager(connManager)
                .evictExpiredConnections().evictIdleConnections(TimeValue.ofMilliseconds(clientConfig.getMaxIdleTime().toMillis()))
                .setUserAgent(clientConfig.getUserAgent());
        return builder.build();
    }

    public static RequestConfig createRequestConfig(HttpClientConfig clientConfig) {
        RequestConfig.Builder builder = RequestConfig.custom();
        if (clientConfig.getKeepAliveDuration() != null) {
            builder.setDefaultKeepAlive(clientConfig.getKeepAliveDuration().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (clientConfig.getConnectTimeout() != null) {
            builder.setConnectTimeout(clientConfig.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS);
            builder.setConnectionRequestTimeout(clientConfig.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (clientConfig.getReadTimeout() != null) {
            builder.setResponseTimeout(clientConfig.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        return builder.build();
    }

    public static IOReactorConfig createReactorConfig(HttpClientConfig clientConfig) {
        final IOReactorConfig.Builder builder = IOReactorConfig.custom();
        if (clientConfig.getWriteTimeout() != null) {
            builder.setSoTimeout((int) clientConfig.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (clientConfig.getIoThreadCount() > 0)
            builder.setIoThreadCount(clientConfig.getIoThreadCount());
        return builder.build();
    }

    public static PoolingAsyncClientConnectionManager createConnectionManager(HttpClientConfig clientConfig) {
        PoolingAsyncClientConnectionManagerBuilder builder = PoolingAsyncClientConnectionManagerBuilder.create();
        if (clientConfig.getDnsResolver() != null) {
            builder.setDnsResolver(new DnsResolver() {
                @Override
                public InetAddress[] resolve(String host) throws UnknownHostException {
                    return clientConfig.getDnsResolver().resolve(host);
                }

                @Override
                public String resolveCanonicalHostname(String host) throws UnknownHostException {
                    return clientConfig.getDnsResolver().resolveCanonicalHostname(host);
                }
            });
        }
        builder.setMaxConnTotal(clientConfig.getMaxConnTotal());
        builder.setMaxConnPerRoute(clientConfig.getMaxRequestsPerHost());

        TlsStrategy tlsStrategy = createTlsStrategy(clientConfig);
        builder.setTlsStrategy(tlsStrategy);

        return builder.build();
    }

    public static TlsStrategy createTlsStrategy(HttpClientConfig clientConfig) {
        ClientTlsStrategyBuilder builder = ClientTlsStrategyBuilder.create();
        try {

            List<TrustManager> trustManagerList = new ArrayList<>();
            X509TrustManager[] trustManagers = clientConfig.getX509TrustManagers();

            if (null != trustManagers) {
                trustManagerList.addAll(Arrays.asList(trustManagers));
            }

            // get trustManager using default certification from jdk
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            trustManagerList.addAll(Arrays.asList(tmf.getTrustManagers()));

            final List<X509TrustManager> finalTrustManagerList = new ArrayList<>();
            for (TrustManager tm : trustManagerList) {
                if (tm instanceof X509TrustManager) {
                    finalTrustManagerList.add((X509TrustManager) tm);
                }
            }
            CompositeX509TrustManager compositeX509TrustManager = new CompositeX509TrustManager(finalTrustManagerList);
            compositeX509TrustManager.setIgnoreSSLCert(clientConfig.isIgnoreSslCerts());
            KeyManager[] keyManagers = null;
            if (clientConfig.getKeyManagers() != null) {
                keyManagers = clientConfig.getKeyManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{compositeX509TrustManager},
                    clientConfig.getSecureRandom());

            HostnameVerifier hostnameVerifier = null;
            if (clientConfig.isIgnoreSslCerts()) {
                hostnameVerifier = new NoopHostnameVerifier();
            } else if (clientConfig.getHostnameVerifier() != null) {
                hostnameVerifier = clientConfig.getHostnameVerifier();
            } else {
                hostnameVerifier = new DefaultHostnameVerifier();
            }

            builder.setHostnameVerifier(hostnameVerifier);
            builder.setSslContext(sslContext);

            return builder.build();
        } catch (Exception e) {
            throw new NopException(ERR_HTTP_INIT_SSL_FAIL, e);
        }
    }
}
