
package io.nop.metadata.service.connection;

import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-07 回归钉·旁路面（plan 2528 裁定⑤）：{@code buildDataSource} DataSource 实例缓存。
 *
 * <p>缺陷形态（pre-fix）：每次调用 {@code new SimpleDataSource}（:289-296 零复用）——同配置
 * 重复建 DataSource 实例。修复后按连接要素 + 超时配置的 SHA-256 摘要键缓存实例
 * （固定常量 maxSize=64 / TTL=5min，零新配置键）：同配置两次获取构造数 == 1（缓存生效）；
 * 凭证轮换（要素变化）→ 摘要变化 → 新实例。
 */
public class TestDataSourceBuildCache_W_07 {

    private static final String BASE_CFG = "\"username\":\"sa\",\"password\":\"\"";

    /** 构造计数缝：覆写包内可重写构造点，统计实际 DataSource 构造次数。 */
    static class CountingProcessor extends MetaDataSourceConnectionProcessor {
        int constructed;

        @Override
        SimpleDataSource newSimpleDataSource(String jdbcUrl, String username, String password,
                                             String driverClassName, Properties handshakeProps) {
            constructed++;
            return super.newSimpleDataSource(jdbcUrl, username, password, driverClassName, handshakeProps);
        }
    }

    private static String h2Config(String memDbName) {
        return "{\"jdbcUrl\":\"jdbc:h2:mem:" + memDbName + "\","
                + "\"driverClassName\":\"org.h2.Driver\"," + BASE_CFG + "}";
    }

    /** 同配置两次 testConnect → DataSource 构造数 == 1（缓存生效；pre-fix 逐调用构造 → red）。 */
    @Test
    public void testSameConfigReusesSingleDataSourceInstance() {
        CountingProcessor service = new CountingProcessor();
        String cfg = h2Config("w07_cache");
        Map<String, Object> first = service.testConnect("jdbc", cfg);
        assertEquals(Boolean.TRUE, first.get("connected"), "first connect must succeed: " + first);
        Map<String, Object> second = service.testConnect("jdbc", cfg);
        assertEquals(Boolean.TRUE, second.get("connected"), "second connect must succeed: " + second);
        assertEquals(1, service.constructed,
                "same-element config must reuse the cached DataSource instance (W-07 cache)");
    }

    /** 凭证轮换（username 变化）→ 摘要键变化 → 新实例（F8 evict 配对第一层）。 */
    @Test
    public void testCredentialRotationBuildsNewInstance() {
        CountingProcessor service = new CountingProcessor();
        service.testConnect("jdbc", h2Config("w07_rot"));
        String rotated = "{\"jdbcUrl\":\"jdbc:h2:mem:w07_rot\","
                + "\"driverClassName\":\"org.h2.Driver\",\"username\":\"sa2\",\"password\":\"\"}";
        Map<String, Object> result = service.testConnect("jdbc", rotated);
        assertEquals(Boolean.TRUE, result.get("connected"), "rotated credential connect must succeed: " + result);
        assertEquals(2, service.constructed,
                "rotated credential (element digest change) must build a new DataSource instance");
    }

    /** 缓存键 = 不可逆摘要：不同要素 → 不同键；同要素 → 同键（键稳定性）。 */
    @Test
    public void testCacheKeyIsIrreversibleDigestOfElements() {
        String k1 = MetaDataSourceConnectionProcessor.cacheKey(
                "jdbc:mysql://example.invalid/db", "u", "p", null, 5);
        String k2 = MetaDataSourceConnectionProcessor.cacheKey(
                "jdbc:mysql://example.invalid/db", "u", "p", null, 5);
        String k3 = MetaDataSourceConnectionProcessor.cacheKey(
                "jdbc:mysql://example.invalid/db", "u2", "p", null, 5);
        assertEquals(k1, k2, "identical elements must produce a stable cache key");
        assertNotEquals(k1, k3, "credential change must change the cache key (rotation evict pairing)");
        assertEquals(64, k1.length(), "SHA-256 hex digest must be 64 chars (irreversible, no plaintext material)");
        assertTrue(!k1.contains("jdbc:") && !k1.contains("u2"),
                "cache key must not contain plaintext connection material");
    }
}
