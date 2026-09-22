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
import io.nop.auth.dao.entity.NopAuthRateLimitCounter;
import io.nop.auth.service.mfa.MfaContacts;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

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
 * {@link ISendCodeRateLimiter} 的 DB 实现（design nop-auth §3.1 db 增补，plan 2275）——
 * 无 Redis 部署经 `nop.auth.rate-limit.store-type=db` 获得集群限流（表
 * `nop_auth_rate_limit_counter`，三类键同表：目标日计数 / IP 日计数 / 间隔门键）。
 * <p>
 * <b>dup-key 分支语义（plan 2275 审查 M3/M4 钉定，两类键相反）</b>：
 * <ul>
 *   <li>日计数键：条件 UPDATE 原子递增（`count=count+1 WHERE key=? AND expire_at&gt;now`）；
 *       affected=0（不存在<b>或已过期</b>）→ 条件 DELETE 过期行 + INSERT(count=1)；
 *       INSERT 撞 PK（并发复活竞态）→ 重试条件 UPDATE。禁止"回退为条件递增"——过期情形会静默丢计数。</li>
 *   <li>间隔门键（恒 SETNX，对齐 Redis 实现）：裸 INSERT 成功 = 占位放行（expire_at=now+interval）；
 *       撞 PK → 读行判定——未过期 = 间隔内拒绝；已过期 = 条件 DELETE + 重试 INSERT（复活）。
 *       禁止无条件拒绝（过期行会永久锁死发码）。</li>
 * </ul>
 * TTL 刷新策略：固定窗口——首次 INSERT 定 expire_at，UPDATE 递增不刷新（对齐 Redis 实现）。
 * 过期行惰性清理（访问路径顺带条件 DELETE，`DbSmsCodeStore` 先例）。原子性裁定同 Redis：
 * 单键 SQL 原子，跨键复合有界竞态漂移可接受（design §3.1）。
 */
public class DbSendCodeRateLimiter implements ISendCodeRateLimiter {

    private static final String KEY_SUFFIX_INTERVAL = ":i";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private IEntityDao<NopAuthRateLimitCounter> dao() {
        return daoProvider.daoFor(NopAuthRateLimitCounter.class);
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
        long now = CoreMetrics.currentTimeMillis();
        long trackerTtl = CFG_AUTH_RATE_TRACKER_EXPIRE.get().toMillis();
        String dayKey = scope + ':' + channel + ':' + target + ":d" + CoreMetrics.today().toEpochDay();

        // 目标层：先递增后检查（被拒尝试消耗日配额，跨后端语义一致）
        long count = incrementCounter(dayKey, now, trackerTtl);

        // 间隔门（恒 SETNX；未过期拒绝 / 过期行删除复活——plan 2275 审查 M3）
        String intervalKey = scope + ':' + channel + ':' + target + KEY_SUFFIX_INTERVAL;
        if (!acquireIntervalMarker(intervalKey, now, intervalSeconds * 1000L)) {
            throw rateLimited(smsErrors, masked);
        }
        if (count > dailyLimit) {
            throw dailyLimited(smsErrors, masked);
        }

        // IP 层（clientIp 可空 = 跳过；channel 隔离同 Local/Redis 实现）
        if (!StringHelper.isEmpty(clientIp)) {
            String ipKey = scope + ':' + channel + ":ip:" + clientIp + ":d" + CoreMetrics.today().toEpochDay();
            long ipCount = incrementCounter(ipKey, now, trackerTtl);
            if (ipCount > ipDailyLimit) {
                throw dailyLimited(smsErrors, masked);
            }
        }
    }

    /**
     * 条件原子递增：affected=0（不存在或已过期）→ 删过期行 + INSERT(count=1)；INSERT 撞 PK
     * → 重试一次条件 UPDATE（并发复活竞态方必然命中新鲜行）。
     */
    private long incrementCounter(String key, long now, long ttlMillis) {
        long expireAt = now + ttlMillis;
        long affected = incrementIfFresh(key, now);
        if (affected > 0) {
            return selectCount(key, now);
        }
        // 行不存在或已过期：删过期行（惰性清理）+ 新日插入
        deleteExpired(key, now);
        try {
            insertCounter(key, 1L, expireAt);
        } catch (NopException e) {
            if (!DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(e.getErrorCode()))
                throw e;
            // 并发竞态：他方已插入新鲜行 → 重试条件递增（仍 0 = 他方行已过期，理论不可达，兜底再删再插）
            if (incrementIfFresh(key, now) > 0) {
                return selectCount(key, now);
            }
            deleteExpired(key, now);
            insertCounter(key, 1L, expireAt);
        }
        return 1L;
    }

    /**
     * 间隔门（SETNX 语义）：INSERT 成功 = 占位放行；撞 PK → 读行判定——未过期 = 间隔内拒绝；
     * 已过期 = 条件 DELETE + 重试 INSERT（复活）；复活竞态再撞 PK = 并发他方刚复活 → 间隔内拒绝。
     */
    private boolean acquireIntervalMarker(String key, long now, long intervalMillis) {
        long expireAt = now + intervalMillis;
        try {
            insertCounter(key, 0L, expireAt);
            return true;
        } catch (NopException e) {
            if (!DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(e.getErrorCode()))
                throw e;
        }
        NopAuthRateLimitCounter existing = dao().getEntityById(key);
        if (existing != null && existing.getExpireAt() != null && existing.getExpireAt() > now) {
            return false; // 间隔窗口内已有发送
        }
        // 过期行：条件 DELETE（仅删过期行，防误删并发复活的新鲜行）+ 重试 INSERT
        deleteExpired(key, now);
        try {
            insertCounter(key, 0L, expireAt);
            return true;
        } catch (NopException e) {
            if (!DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(e.getErrorCode()))
                throw e;
            return false; // 并发他方刚复活 → 间隔内拒绝
        }
    }

    private long incrementIfFresh(String key, long now) {
        SQL upd = SQL.begin().name("authRateLimitIncrement")
                .sql("update NopAuthRateLimitCounter o set o.counterCount = o.counterCount + 1 "
                        + "where o.counterKey = ? and o.expireAt > ?", key, now)
                .end();
        return ormTemplate.executeUpdate(upd);
    }

    private long deleteExpired(String key, long now) {
        SQL del = SQL.begin().name("authRateLimitDeleteExpired")
                .sql("delete from NopAuthRateLimitCounter o where o.counterKey = ? and o.expireAt <= ?", key, now)
                .end();
        return ormTemplate.executeUpdate(del);
    }

    private long selectCount(String key, long now) {
        NopAuthRateLimitCounter row = dao().getEntityById(key);
        if (row == null || (row.getExpireAt() != null && row.getExpireAt() <= now))
            return 0L;
        return row.getCounterCount() == null ? 0L : row.getCounterCount();
    }

    private void insertCounter(String key, long count, long expireAt) {
        NopAuthRateLimitCounter e = dao().newEntity();
        e.setCounterKey(key);
        e.setCounterCount(count);
        e.setExpireAt(expireAt);
        dao().saveEntityDirectly(e);
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
