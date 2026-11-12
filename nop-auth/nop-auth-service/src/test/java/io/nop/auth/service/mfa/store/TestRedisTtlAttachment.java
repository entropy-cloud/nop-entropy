package io.nop.auth.service.mfa.store;

import io.nop.auth.service.login.RedisLoginAttemptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.1-MA-02（plan 2569）回归钉：Redis TTL 附着族——INCRBY 成功后 PEXPIRE 瞬时故障曾使
 * 键以无 TTL 形态残留（锁号计数键永不清理 = 人工 DEL 才能解锁）。
 *
 * <p>修复形态：setTtl 失败 → 补偿删除键 + ERROR 日志 → 后续递增以 v=1 重设 TTL
 * （无永久毒键）。补偿删除的语义裁定（owner doc 同步）：attempt 族计数丢失无害
 * （登录本已失败）；limiter 族当日配额重置（劣于毒键锁死配额，且日键有界）；
 * code 族重取验证码（无害）。
 */
class TestRedisTtlAttachment {

    private FakeNosqlService nosql;

    @BeforeEach
    void setUp() {
        nosql = new FakeNosqlService();
    }

    // ===================== 红① 登录失败计数键：瞬时 PEXPIRE 故障不得留毒键 =====================

    @Test
    void transientTtlFailureMustNotLeaveTtlLessLoginAttemptKey() {
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(nosql);
        store.setLoginFailTimeout(Duration.ofMinutes(10));
        String key = "un:ttl-user";

        // 首次递增 + TTL 设置瞬时故障：异常上抛（旧代码形态），键不得残留
        nosql.injectNextSetTimeoutFail();
        assertThrows(RuntimeException.class, () -> store.incrementLoginFailCount(key));
        assertNull(nosql.counterTtl.get(RedisLoginAttemptStore.KEY_PREFIX + key),
                "key must not stay TTL-less after transient pexpire failure (compensation deletes)");
        assertTrue(nosql.counter(RedisLoginAttemptStore.KEY_PREFIX + key).get() == 0L,
                "poison TTL-less key must be deleted by compensation");

        // 恢复后下次递增：v=1 重设 TTL（非毒键形态）
        long v = store.incrementLoginFailCount(key);
        assertTrue(v == 1L, "counter restarts from zero after compensation delete, got " + v);
        assertNotNull(nosql.counterTtl.get(RedisLoginAttemptStore.KEY_PREFIX + key),
                "recovered increment must re-attach TTL");
    }

    @Test
    void normalIncrementAttachesTtlOnce() {
        RedisLoginAttemptStore store = new RedisLoginAttemptStore(nosql);
        store.setLoginFailTimeout(Duration.ofMinutes(10));
        String key = "un:normal-user";

        store.incrementLoginFailCount(key);
        assertNotNull(nosql.counterTtl.get(RedisLoginAttemptStore.KEY_PREFIX + key));
        // 后续递增不刷新 TTL（既有裁定）：无异常、计数继续
        assertTrue(store.incrementLoginFailCount(key) == 2L);
    }

    // ===================== 红② 统一通道：RedisTtlGuard 补偿语义（7 站点共用载体） =====================

    @Test
    void ttlGuardTransientFailureMustNotLeaveTtlLessKey() {
        // RedisSendCodeRateLimiter 两站点经同一通道（编译面接线 + grep 核清），补偿语义同载体
        String counterKey = "auth:send-code:login:sms:13800000000:d0";
        nosql.counter(counterKey).increment(1);

        nosql.injectNextSetTimeoutFail();
        assertThrows(RuntimeException.class,
                () -> io.nop.auth.service.support.RedisTtlGuard.attachTtl(nosql, counterKey,
                        Duration.ofHours(25), org.slf4j.LoggerFactory.getLogger("test")));
        assertTrue(nosql.counter(counterKey).get() == 0L,
                "poison TTL-less rate-limit key must be deleted by compensation");

        // 恢复：下次附着成功
        io.nop.auth.service.support.RedisTtlGuard.attachTtl(nosql, counterKey,
                Duration.ofHours(25), org.slf4j.LoggerFactory.getLogger("test"));
        assertNotNull(nosql.counterTtl.get(counterKey),
                "recovered attach must set TTL");
    }
}
