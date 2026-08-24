/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SslVerifyMode;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dockerless unit tests for {@link LettuceRedisConnectionProvider}: connection lifecycle and
 * node-address parsing do not require a live Redis server because clients connect lazily.
 */
public class TestLettuceRedisConnectionProvider {

    @Test
    void testClusterNodeAddressValidation() {
        LettuceRedisConnectionProvider provider = new LettuceRedisConnectionProvider();
        RedisConfig config = new RedisConfig();
        config.setClusterNodes(Collections.singletonList("bad-host-no-port"));
        provider.setConfig(config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, provider::start);
        assertTrue(ex.getMessage().contains("bad-host-no-port"),
                "error message should contain the offending node address: " + ex.getMessage());
    }

    @Test
    void testClusterNodeAddressPortValidation() {
        LettuceRedisConnectionProvider provider = new LettuceRedisConnectionProvider();
        RedisConfig config = new RedisConfig();
        config.setClusterNodes(Collections.singletonList("host:not-a-port"));
        provider.setConfig(config);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, provider::start);
        assertTrue(ex.getMessage().contains("host:not-a-port"));
    }

    @Test
    void testClusterNodeIpv6AddressParsing() throws Exception {
        LettuceRedisConnectionProvider provider = new LettuceRedisConnectionProvider();
        RedisConfig config = new RedisConfig();
        config.setClusterNodes(Arrays.asList("[::1]:6379", "node-a:7000"));
        provider.setConfig(config);

        List<RedisURI> uris = invokeBuildClusterURIs(provider);
        assertEquals(2, uris.size());
        assertEquals("::1", uris.get(0).getHost());
        assertEquals(6379, uris.get(0).getPort());
        assertEquals("node-a", uris.get(1).getHost());
        assertEquals(7000, uris.get(1).getPort());
    }

    @Test
    void testSslVerifyPeerDefaultsToTrue() throws Exception {
        LettuceRedisConnectionProvider provider = new LettuceRedisConnectionProvider();
        RedisConfig config = new RedisConfig();
        config.setUseSsl(true);
        provider.setConfig(config);

        RedisURI uri = invokeBuildRedisURI(provider);
        assertTrue(uri.isSsl());
        assertEquals(SslVerifyMode.FULL, uri.getVerifyMode());
    }

    @Test
    void testSslVerifyPeerCanBeDisabledExplicitly() throws Exception {
        LettuceRedisConnectionProvider provider = new LettuceRedisConnectionProvider();
        RedisConfig config = new RedisConfig();
        config.setUseSsl(true);
        config.setVerifyPeer(false);
        provider.setConfig(config);

        RedisURI uri = invokeBuildRedisURI(provider);
        assertEquals(SslVerifyMode.NONE, uri.getVerifyMode());
    }

    @Test
    void testSentinelNodesWiredIntoUri() throws Exception {
        LettuceRedisConnectionProvider provider = new LettuceRedisConnectionProvider();
        RedisConfig config = new RedisConfig();
        config.setMasterName("mymaster");
        config.setSentinelNodes(Arrays.asList("sentinel-a:26379", "[::1]:26380"));
        provider.setConfig(config);

        RedisURI uri = invokeBuildRedisURI(provider);
        assertEquals("mymaster", uri.getSentinelMasterId());
        assertEquals(2, uri.getSentinels().size());
        assertEquals("sentinel-a", uri.getSentinels().get(0).getHost());
        assertEquals(26379, uri.getSentinels().get(0).getPort());
        assertEquals("::1", uri.getSentinels().get(1).getHost());
        assertEquals(26380, uri.getSentinels().get(1).getPort());
    }

    @SuppressWarnings("unchecked")
    private static List<RedisURI> invokeBuildClusterURIs(LettuceRedisConnectionProvider provider) throws Exception {
        Method m = LettuceRedisConnectionProvider.class.getDeclaredMethod("buildClusterURIs");
        m.setAccessible(true);
        return (List<RedisURI>) m.invoke(provider);
    }

    private static RedisURI invokeBuildRedisURI(LettuceRedisConnectionProvider provider) throws Exception {
        Method m = LettuceRedisConnectionProvider.class.getDeclaredMethod("buildRedisURI");
        m.setAccessible(true);
        return (RedisURI) m.invoke(provider);
    }
}
