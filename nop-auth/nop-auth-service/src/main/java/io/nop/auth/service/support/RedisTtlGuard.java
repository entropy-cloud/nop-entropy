/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.support;

import io.nop.api.core.util.FutureHelper;
import io.nop.nosql.core.INosqlService;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * 5.1-MA-02（plan 2569）Redis TTL 附着统一通道：INCRBY 成功后的 PEXPIRE 不得以
 * 「失败但键残留」收场——无 TTL 键在 attempt 族永不清理（人工 DEL 才能解锁）。
 *
 * <p>失败语义（补偿删除 + ERROR 后原样上抛）：attempt 族计数丢失无害（登录本已失败，
 * 下次递增 v=1 重设 TTL）；limiter 族当日配额重置（劣于毒键锁死配额，且日键有界）；
 * code 族重取验证码（无害）。三族共同点：键不得以无 TTL 形态残留。
 */
public final class RedisTtlGuard {

    private RedisTtlGuard() {
    }

    /**
     * 附着 TTL；失败时补偿删除该键（含 counter 形态）并原样上抛异常。
     * ttl 非正值 = 不附着（配置关闭，与各 store 既有裁定一致）。
     */
    public static void attachTtl(INosqlService nosql, String redisKey, Duration ttl, Logger log) {
        if (ttl == null || ttl.isNegative() || ttl.isZero())
            return;
        try {
            FutureHelper.syncGet(nosql.setTimeoutAsync(redisKey, ttl.toMillis()));
        } catch (Exception e) {
            log.error("nop.auth.redis-ttl-attach-fail-compensate-delete:key={}", redisKey, e);
            try {
                nosql.remove(redisKey);
            } catch (Exception e2) {
                log.error("nop.auth.redis-ttl-compensate-delete-fail:key={}", redisKey, e2);
            }
            throw e;
        }
    }
}
