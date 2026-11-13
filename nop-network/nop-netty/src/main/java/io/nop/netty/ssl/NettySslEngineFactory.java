package io.nop.netty.ssl;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.netty.config.SslConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

public class NettySslEngineFactory implements ISslEngineFactory {
    @Override
    public SslContext getSslContext(SslConfig sslConfig, boolean clientMode) {
        // 4.3-C1-014（plan 2561 裁定 A）：原双路径 check-then-set 无锁——并发首装会重复
        // buildSslContext（keystore 反复加载 + SslContext 双建，其一弃而泄漏），并可能发布
        // 未完全构造的上下文。收敛为以 sslConfig 为锁粒度的 synchronized 惰性块 + SslConfig
        // 缓存字段 volatile（double-check 于锁内复读，早退路径读 volatile 可见）。
        if (clientMode) {
            synchronized (sslConfig) {
                if (sslConfig.getClientSslContext() != null)
                    return sslConfig.getClientSslContext();
                SslContext sslContext = buildSslContext(sslConfig, clientMode);
                sslConfig.setClientSslContext(sslContext);
                return sslContext;
            }
        } else {
            synchronized (sslConfig) {
                if (sslConfig.getServerSslContext() != null) {
                    return sslConfig.getServerSslContext();
                }
                SslContext sslContext = buildSslContext(sslConfig, clientMode);
                sslConfig.setServerSslContext(sslContext);
                return sslContext;
            }
        }
    }

    /**
     * 构建点（protected seam 供测试注入计数桩——对齐 TencentEmailSender.createClient 先例）。
     */
    protected SslContext buildSslContext(SslConfig sslConfig, boolean clientMode) {
        InputStream in = null;
        try {
            String sslKeyStoreType = sslConfig.getKeyStoreType();
            if (sslKeyStoreType == null) {
                throw new IllegalArgumentException("nop.err.ssl.key-store-type-not-set");
            }

            KeyStore ks = KeyStore.getInstance(sslKeyStoreType);
            String sslKeyStore = sslConfig.getKeyStorePath();
            if (sslKeyStore == null) {
                throw new IllegalArgumentException("nop.err.ssl.key-store-path-not-set");
            }
            in = new FileInputStream(sslKeyStore);
            String keyStorePass = sslConfig.getKeyStorePassword();
            if (keyStorePass == null) {
                throw new IllegalArgumentException("nop.err.ssl.key-store-password-not-set");
            }
            char[] passChs = keyStorePass.toCharArray();
            ks.load(in, passChs);
            String serverSslAlgorithm = sslConfig.getServerSslAlgorithm();
            if (serverSslAlgorithm == null) {
                serverSslAlgorithm = KeyManagerFactory
                        .getDefaultAlgorithm();
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(serverSslAlgorithm);
            kmf.init(ks, passChs);
            String sslAlgorithm = sslConfig.getClientSslAlgorithm();
            if (sslAlgorithm == null) {
                sslAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(sslAlgorithm);
            KeyStore trustStore = loadTrustStore(sslConfig, sslKeyStoreType);
            if (trustStore != null) {
                // 显式配置的 trustStore 优先于 keyStore
                tmf.init(trustStore);
            } else {
                tmf.init(ks);
            }

            SslContextBuilder builder;
            if (clientMode) {
                builder = SslContextBuilder.forClient().keyManager(kmf).trustManager(tmf);
            } else {
                builder = SslContextBuilder.forServer(kmf).trustManager(tmf);
                if (sslConfig.isNeedClientAuth()) {
                    builder.clientAuth(ClientAuth.REQUIRE);
                }
            }
            return builder.build();
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(in);
        }
    }

    private KeyStore loadTrustStore(SslConfig sslConfig, String defaultType) throws Exception {
        String trustStorePath = sslConfig.getTrustStorePath();
        if (trustStorePath == null)
            return null;

        String type = defaultType != null ? defaultType : KeyStore.getDefaultType();
        KeyStore ts = KeyStore.getInstance(type);
        char[] pass = sslConfig.getTrustStorePassword() != null
                ? sslConfig.getTrustStorePassword().toCharArray() : null;
        try (InputStream tin = new FileInputStream(trustStorePath)) {
            ts.load(tin, pass);
        }
        return ts;
    }
}
