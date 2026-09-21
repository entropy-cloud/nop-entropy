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
import io.nop.auth.service.mfa.MfaContacts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_RATE_TRACKER_EXPIRE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_RATE_TRACKER_MAX_SIZE;
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
 * {@link ISendCodeRateLimiter} 的 Local（JVM 内）实现——plan 2274 Phase 1 平移原
 * LoginServiceImpl / NopAuthUserBizModel 的 Caffeine 限流 Map，行为等价：
 * <ul>
 *   <li>先递增后检查：compute 内先 count+1，再检查间隔/日配额（被拒尝试消耗日配额）。</li>
 *   <li>间隔检查与 lastSendMs 占用在同一原子区（synchronized map + compute），
 *       并发突发同目标恰好放行 1 个（TestLocalSendCodeRateLimiter 并发回归钉定）。</li>
 *   <li>计数按自然日重置（entry 携带 epochDay）；Map 硬上限 + 过期兜底清理跨天残留键
 *       （{@code nop.auth.rate-limit.tracker-max-size / tracker-expire}）。</li>
 * </ul>
 * scope 经键前缀隔离（{@code scope:target} / {@code scope:ip:ip}）——等价原两类各自的 Map 实例。
 */
public class LocalSendCodeRateLimiter implements ISendCodeRateLimiter {

    /** 目标维度：key → [lastSendMs, dailyCount, dailyDate]。 */
    private final Map<String, long[]> targetTrackers = newBoundedRateMap();

    /** IP 维度：key → [dailyCount, dailyDate]。 */
    private final Map<String, long[]> ipTrackers = newBoundedRateMap();

    private static Map<String, long[]> newBoundedRateMap() {
        Cache<String, long[]> cache = Caffeine.newBuilder()
                .maximumSize(CFG_AUTH_RATE_TRACKER_MAX_SIZE.get())
                .expireAfterWrite(CFG_AUTH_RATE_TRACKER_EXPIRE.get()).build();
        return cache.asMap();
    }

    @Override
    public void checkSmsAllowed(String scope, String phone, String clientIp) {
        checkTarget(scope, "sms", phone, clientIp,
                CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS.get(), CFG_AUTH_SMS_CODE_DAILY_LIMIT.get(),
                CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT.get(), true);
    }

    @Override
    public void checkEmailAllowed(String scope, String email, String clientIp) {
        checkTarget(scope, "email", email, clientIp,
                CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS.get(), CFG_AUTH_EMAIL_CODE_DAILY_LIMIT.get(),
                CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT.get(), false);
    }

    private void checkTarget(String scope, String channel, String target, String clientIp,
                             int intervalSeconds, int dailyLimit, int ipDailyLimit, boolean smsErrors) {
        long now = CoreMetrics.currentTimeMillis();
        long today = CoreMetrics.today().toEpochDay();
        String masked = smsErrors ? MfaContacts.maskPhone(target) : MfaContacts.maskEmail(target);

        // 目标层：间隔 + 日上限（先递增后检查；间隔检查与 lastSendMs 占用同原子区）
        synchronized (targetTrackers) {
            long[] entry = targetTrackers.compute(scope + ':' + target, (k, v) -> {
                if (v == null || v[2] != today) {
                    return new long[]{now, 1, today};
                }
                return new long[]{v[0], v[1] + 1, today};
            });
            if (entry[1] > 1 && (now - entry[0]) < intervalSeconds * 1000L) {
                throw rateLimited(smsErrors, masked);
            }
            if (entry[1] > dailyLimit) {
                throw dailyLimited(smsErrors, masked);
            }
            entry[0] = now;
        }

        // IP 层（clientIp 可空 = 跳过——bind-sms proof 路径现状无 IP 维度；
        // 键含 channel：原实现 sms-IP 与 email-IP 为独立 Map，维持隔离）
        checkIp(scope, channel, clientIp, ipDailyLimit, smsErrors, masked);
    }

    private void checkIp(String scope, String channel, String clientIp, int ipDailyLimit, boolean smsErrors,
                         String masked) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }
        long today = CoreMetrics.today().toEpochDay();
        long[] ipEntry = ipTrackers.compute(scope + ':' + channel + ":ip:" + clientIp, (k, v) -> {
            if (v == null || v[1] != today) {
                return new long[]{1, today};
            }
            return new long[]{v[0] + 1, today};
        });
        if (ipEntry[0] > ipDailyLimit) {
            throw dailyLimited(smsErrors, masked);
        }
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
