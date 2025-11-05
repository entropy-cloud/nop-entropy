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
        if (clientMode) {
            if (sslConfig.getClientSslContext() != null)
                return sslConfig.getClientSslContext();
            SslContext sslContext = buildSslContext(sslConfig, clientMode);
            sslConfig.setClientSslContext(sslContext);
            return sslContext;
        } else {
            if (sslConfig.getServerSslContext() != null) {
                return sslConfig.getServerSslContext();
            }
            SslContext sslContext = buildSslContext(sslConfig, clientMode);
            sslConfig.setServerSslContext(sslContext);
            return sslContext;
        }
    }

    private SslContext buildSslContext(SslConfig sslConfig, boolean clientMode) {
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
            tmf.init(ks);

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
}
