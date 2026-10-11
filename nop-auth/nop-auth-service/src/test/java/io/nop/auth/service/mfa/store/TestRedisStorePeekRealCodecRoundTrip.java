package io.nop.auth.service.mfa.store;

import io.nop.commons.util.ClassHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.PrefixEncodeHelper;
import io.nop.auth.core.mfa.store.MfaChallengeStoreConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R1.8 / P1-check2-nosql-67：MFA 票据 verifiedAt 经真实序列化往返后可读。
 *
 * <p>缺陷根因（3.10 审计 §2.4）：FakeNosqlService 在内存中存原对象、绕过真实序列化，
 * PrefixEncodeHelper 将 Long 编码为裸十进制文本、解码恒返回 String，
 * 使 {@code peek} 的 {@code instanceof Number} 恒 false → verifiedAt 假 null。
 * 本测试以真实 PrefixEncodeHelper 编解码包住传输桩（FakeNosqlService 仅模拟传输），
 * 复现 Redis 部署下的实际字节往返。
 */
public class TestRedisStorePeekRealCodecRoundTrip {

    @BeforeAll
    static void init() {
        // PrefixEncodeHelper.encode(MfaChallenge) 走 JSON.stringify——需要 JSON provider 注册
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /** 传输桩：写入值经真实 PrefixEncodeHelper 编码、读出经真实解码（模拟 PrefixTextCodec 字节往返）。 */
    private static MfaChallengeStoreConfig cfg(int expireSeconds) {
        MfaChallengeStoreConfig c = new MfaChallengeStoreConfig();
        c.setExpireSeconds(expireSeconds);
        return c;
    }

    static class CodecSerializingNosql extends FakeNosqlService {
        @Override
        public CompletionStage<Void> putExAsync(String key, Object value, long timeout) {
            return super.putExAsync(key, PrefixEncodeHelper.encode(value), timeout);
        }

        @Override
        public CompletionStage<Boolean> putIfAbsentExAsync(String key, Object value, long timeout) {
            // markVerified 的票写入走 SETNX（putIfAbsentExAsync），同样须过真实序列化
            return super.putIfAbsentExAsync(key, PrefixEncodeHelper.encode(value), timeout);
        }

        @Override
        public Object get(String key) {
            Object raw = super.get(key);
            if (raw instanceof String) {
                return PrefixEncodeHelper.decode((String) raw, ClassHelper.getSafeClassLoader());
            }
            return raw;
        }
    }

    @Test
    public void testVerifiedAtSurvivesRealCodecRoundTrip() {
        CodecSerializingNosql nosql = new CodecSerializingNosql();
        RedisMfaChallengeStore store = new RedisMfaChallengeStore(nosql, cfg(60));

        String token = store.create("user-9", "totp", 1, "t", null);
        assertNull(store.peek(token).getVerifiedAt(), "freshly created must be unverified");

        assertTrue(store.markVerified(token), "markVerified must succeed");

        // 修复前：票值经真实编解码为 String，instanceof Number 恒 false → verifiedAt 假 null
        assertNotNull(store.peek(token).getVerifiedAt(),
                "verifiedAt must survive real PrefixEncodeHelper roundtrip (P1-check2-nosql-67)");
    }

    @Test
    public void testUnverifiedStaysNullThroughRealCodecRoundTrip() {
        CodecSerializingNosql nosql = new CodecSerializingNosql();
        RedisMfaChallengeStore store = new RedisMfaChallengeStore(nosql, cfg(60));

        String token = store.create("user-10", "totp", 1, "t", null);
        assertNull(store.peek(token).getVerifiedAt(), "unverified stays null through roundtrip");
    }
}
