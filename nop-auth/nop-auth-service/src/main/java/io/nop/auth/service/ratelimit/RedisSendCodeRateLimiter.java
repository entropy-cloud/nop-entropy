/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.service.mfa.MfaContacts;
import io.nop.commons.util.StringHelper;
import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlService;

import jakarta.inject.Inject;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_RATE_TRACKER_EXPIRE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS;
import static io.nop.auth.service.NopAuthErrors.ARG_CHANNEL;
import static io.nop.auth.service.NopAuthErrors.ARG_PHONE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_RATE_LIMITED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_RATE_LIMITED;

/**
 * {@link ISendCodeRateLimiter} 的 Redis 实现（design nop-auth §3.1，plan 2274 Phase 1）——
 * 多节点部署下全实例共享限流计数。
 * <p>
 * <b>原子性契约（诚实表述）</b>：单键原子——日配额用 {@link INosqlCounter#increment}(INCRBY，
 * 键含日界后缀，首次递增经 {@code setTimeoutAsync} 设 TTL)；间隔用 {@code putIfAbsentExAsync}
 * (SETNX+PX) 占位（TTL=interval），当日首次发送用 {@code putExAsync} 覆盖刷新。跨键复合
 * （目标计数 / IP 计数 / 间隔标记三键）不保证全局原子，并发下存在有界竞态漂移（最坏多放过
 * 个位数请求），与 Local 有界 Map 的驱逐漂移同类，已裁定可接受（design §3.1 拒绝过度承诺）。
 * <p>
 * 操作顺序 = 先递增后检查，与 Local 实现一致（被拒尝试消耗日配额）。
 * <p>
 * 键拓扑：{@code auth:rl:{scope}:{channel}:{target}:d{day}}（目标日计数）/
 * {@code auth:rl:{scope}:{channel}:{target}:i}（间隔标记）/
 * {@code auth:rl:{scope}:{channel}:ip:{ip}:d{day}}（IP 日计数，channel 隔离同 Local）。
 * <p>
 * 连通性证据（无嵌入式 Redis 测试基建）：本类逐条委托 nop-nosql 原语，行为经
 * {@code FakeNosqlService}（记录原语调用）验证委托契约，与 {@code RedisMfaChallengeStore}
 * 测试形态一致。
 */
public class RedisSendCodeRateLimiter implements ISendCodeRateLimiter {

    private static final String KEY_PREFIX = "auth:rl:";

    private final INosqlService nosql;

    @Inject
    public RedisSendCodeRateLimiter(INosqlService nosql) {
        this.nosql = nosql;
    }

    @Override
    public void checkSmsAllowed(String scope, String phone, String clientIp) {
        check(scope, "sms", phone, clientIp,
                CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS.get(), CFG_AUTH_SMS_CODE_DAILY_LIMIT.get(),
                CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT.get(), true);
    }

    @Override
    public void checkEmailAllowed(String scope, String email, String clientIp) {
        check(scope, "email", email, clientIp,
                CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS.get(), CFG_AUTH_EMAIL_CODE_DAILY_LIMIT.get(),
                CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT.get(), false);
    }

    private void check(String scope, String channel, String target, String clientIp,
                       int intervalSeconds, int dailyLimit, int ipDailyLimit, boolean smsErrors) {
        String masked = smsErrors ? MfaContacts.maskPhone(target) : MfaContacts.maskEmail(target);
        String counterKey = dayKey(scope, channel, target);
        String intervalKey = KEY_PREFIX + scope + ':' + channel + ':' + target + ":i";

        // 目标层：先递增后检查（被拒尝试消耗日配额，跨后端语义一致）
        long count = nosql.counter(counterKey).increment(1);
        if (count == 1L) {
            // 首次递增设 TTL（后续不刷新——日界键自然过期即跨天重置；PEXPIRE 先例 RedisMfaChallengeStore）
            FutureHelper.syncGet(nosql.setTimeoutAsync(counterKey, trackerTtlMillis()));
        }
        // 间隔门 = 单键原子 SETNX+PX：并发突发同目标恰好一个占位成功（含当日首次——
        // INCR 与 SETNX 的固定先后序消除双写竞态；跨键复合的残存漂移见类注释）
        if (!Boolean.TRUE.equals(
                FutureHelper.syncGet(nosql.putIfAbsentExAsync(intervalKey, Boolean.TRUE, intervalSeconds * 1000L)))) {
            throw rateLimited(smsErrors, masked);
        }
        if (count > dailyLimit) {
            throw dailyLimited(smsErrors, masked);
        }

        // IP 层（clientIp 可空 = 跳过；channel 隔离同 Local）
        if (!StringHelper.isEmpty(clientIp)) {
            String ipKey = KEY_PREFIX + scope + ':' + channel + ":ip:" + clientIp + ":d" + CoreMetrics.today().toEpochDay();
            long ipCount = nosql.counter(ipKey).increment(1);
            if (ipCount == 1L) {
                FutureHelper.syncGet(nosql.setTimeoutAsync(ipKey, trackerTtlMillis()));
            }
            if (ipCount > ipDailyLimit) {
                throw dailyLimited(smsErrors, masked);
            }
        }
    }

    private String dayKey(String scope, String channel, String target) {
        return KEY_PREFIX + scope + ':' + channel + ':' + target + ":d" + CoreMetrics.today().toEpochDay();
    }

    private long trackerTtlMillis() {
        return CFG_AUTH_RATE_TRACKER_EXPIRE.get().toMillis();
    }

    private NopException rateLimited(boolean smsErrors, String masked) {
        return smsErrors
                ? new NopException(ERR_AUTH_SMS_RATE_LIMITED).param(ARG_PHONE, masked)
                : new NopException(ERR_AUTH_EMAIL_RATE_LIMITED).param(ARG_CHANNEL, masked);
    }

    private NopException dailyLimited(boolean smsErrors, String masked) {
        return smsErrors
                ? new NopException(ERR_AUTH_SMS_DAILY_LIMIT).param(ARG_PHONE, masked)
                : new NopException(ERR_AUTH_EMAIL_DAILY_LIMIT).param(ARG_CHANNEL, masked);
    }
}
