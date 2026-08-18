/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStoreConfig;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A2-audit D2-F1 对抗探查回归（P1 修复验证，audit
 * {@code ai-dev/audits/2026-08/2026-08-18-1244-deep-audit-nop-auth/D2-anti-replay-one-time-consumption.md}）：
 * Redis 码 store 的 VALID 裁决必须以 {@code removeIfMatch}（Lua CAS）胜出为前提——并发双
 * verify 同码仅胜者 VALID，败者 EXPIRED（与 Db 实现的 affected==0 → EXPIRED 语义对齐，双花防御）。
 * <p>
 * 竞态确定性复现：{@link CasLoserNosql} 模拟"另一并发验证者刚赢得 CAS"（removeIfMatch 恒
 * false、不动存储），无需真实并发编排。
 */
class TestRedisCodeStoreCasRace {

    /** 模拟并发竞争者已消费：CAS 恒败（键仍在——败者随后的 EXPIRED 裁决来自 CAS 结果而非键缺失）。 */
    static class CasLoserNosql extends FakeNosqlService {
        @Override
        public boolean removeIfMatch(String key, Object object) {
            record("removeIfMatch");
            return false;
        }
    }

    @Test
    void smsVerifyLosingCasMustReturnExpiredNotValid() {
        RedisSmsCodeStore store = new RedisSmsCodeStore(new CasLoserNosql(), smsCfg(60, 5));
        String code = store.send("mfa:user-race");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa:user-race", code),
                "CAS loser (concurrent consumer won) must NOT be adjudicated VALID (A2-audit D2-F1)");
    }

    @Test
    void emailVerifyLosingCasMustReturnExpiredNotValid() {
        RedisEmailCodeStore store = new RedisEmailCodeStore(new CasLoserNosql(), emailCfg(60, 5));
        String code = store.send("mfa-email:user-race");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-race", code),
                "CAS loser (concurrent consumer won) must NOT be adjudicated VALID (A2-audit D2-F1)");
    }

    @Test
    void smsCasWinnerStillValidThenExpired() {
        RedisSmsCodeStore store = new RedisSmsCodeStore(new FakeNosqlService(), smsCfg(60, 5));
        String code = store.send("mfa:user-win");
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa:user-win", code),
                "CAS winner must be adjudicated VALID (normal path unaffected)");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa:user-win", code),
                "consumed code must be EXPIRED afterwards");
    }

    @Test
    void emailCasWinnerStillValidThenExpired() {
        RedisEmailCodeStore store = new RedisEmailCodeStore(new FakeNosqlService(), emailCfg(60, 5));
        String code = store.send("mfa-email:user-win");
        assertEquals(CodeVerifyResult.VALID, store.verify("mfa-email:user-win", code),
                "CAS winner must be adjudicated VALID (normal path unaffected)");
        assertEquals(CodeVerifyResult.EXPIRED, store.verify("mfa-email:user-win", code),
                "consumed code must be EXPIRED afterwards");
    }

    private static SmsCodeStoreConfig smsCfg(int expireSeconds, int maxAttempts) {
        SmsCodeStoreConfig c = new SmsCodeStoreConfig();
        c.setExpireSeconds(expireSeconds);
        c.setMaxAttempts(maxAttempts);
        return c;
    }

    private static EmailCodeStoreConfig emailCfg(int expireSeconds, int maxAttempts) {
        EmailCodeStoreConfig c = new EmailCodeStoreConfig();
        c.setExpireSeconds(expireSeconds);
        c.setMaxAttempts(maxAttempts);
        return c;
    }
}
