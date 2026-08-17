/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.EmailCodeStoreConfig;
import io.nop.auth.dao.entity.NopAuthEmailCode;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;

import jakarta.inject.Inject;

import static io.nop.dao.DaoConstants.DEFAULT_QUERY_SPACE;

/**
 * {@link EmailCodeStore} 的数据库实现（设计 §5.3.3，W15-impl）。
 * <p>
 * 三态语义与 {@code DbSmsCodeStore} 逐条对齐（表 nop_auth_email_code 结构对齐 nop_auth_sms_code）：
 * <ul>
 *   <li>{@code send}：6 位随机码 + INSERT（重发为 upsert：覆盖旧码 + 重置 failCount）。</li>
 *   <li>{@code verify}：VALID 条件 DELETE 一次性消费 / EXPIRED 惰性删除 / MISMATCH SQL 原子递增
 *       failCount 达 max-attempts 作废。</li>
 *   <li>{@code consume}：显式 DELETE。</li>
 * </ul>
 * EMAIL 列为审计/信息性列：email 码无邮箱登录场景（设计 §5.3.3——无独立公开发码端点），
 * 逻辑 key（{@code mfa-email:{userId}} / {@code proof-email:{userId}}）不含邮箱地址，该列可空。
 */
public class DbEmailCodeStore implements EmailCodeStore {

    static final String TABLE = "nop_auth_email_code";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    private EmailCodeStoreConfig config = new EmailCodeStoreConfig();

    public void setConfig(EmailCodeStoreConfig config) {
        this.config = config;
    }

    private IEntityDao<NopAuthEmailCode> dao() {
        return daoProvider.daoFor(NopAuthEmailCode.class);
    }

    private long ttlMillis() {
        return config.getExpireSeconds() * 1000L;
    }

    @Override
    public String send(String key) {
        long now = System.currentTimeMillis();
        String code = String.format("%06d", MathHelper.secureRandom().nextInt(1_000_000));
        NopAuthEmailCode existing = dao().getEntityById(key);
        if (existing != null) {
            // 重发：覆盖旧码 + 重置失败计数（新周期）
            existing.setCode(code);
            existing.setExpireAt(now + ttlMillis());
            existing.setFailCount(0);
            dao().updateEntityDirectly(existing);
        } else {
            NopAuthEmailCode e = dao().newEntity();
            e.setCodeKey(key);
            e.setCode(code);
            e.setExpireAt(now + ttlMillis());
            e.setFailCount(0);
            dao().saveEntityDirectly(e);
        }
        return code;
    }

    @Override
    public CodeVerifyResult verify(String key, String code) {
        if (StringHelper.isEmpty(code))
            return CodeVerifyResult.MISMATCH;
        NopAuthEmailCode e = dao().getEntityById(key);
        if (e == null)
            return CodeVerifyResult.EXPIRED;
        long now = System.currentTimeMillis();
        if (e.getExpireAt() != null && e.getExpireAt() <= now) {
            deleteByKey(key); // 惰性清理过期行
            return CodeVerifyResult.EXPIRED;
        }
        if (code.equals(e.getCode())) {
            // VALID：条件 DELETE 一次性消费（并发仅一个 affected>0）
            long affected = conditionalDelete(key, now);
            return affected > 0 ? CodeVerifyResult.VALID : CodeVerifyResult.EXPIRED;
        }
        // MISMATCH：原子递增失败计数
        long affected = incrFail(key, now);
        if (affected == 0)
            return CodeVerifyResult.EXPIRED; // 行已不存在（竞态/被消费）
        int fails = readFailCount(key);
        if (fails >= config.getMaxAttempts()) {
            // 超限作废：删验证码
            deleteByKey(key);
        }
        return CodeVerifyResult.MISMATCH;
    }

    @Override
    public void consume(String key) {
        if (StringHelper.isEmpty(key))
            return;
        deleteByKey(key);
    }

    private long incrFail(String key, long now) {
        SQL incr = SQL.begin().name("emailCodeIncrFail").querySpace(DEFAULT_QUERY_SPACE)
                .sql("UPDATE " + TABLE + " SET FAIL_COUNT = FAIL_COUNT + 1 WHERE CODE_KEY = ? AND EXPIRE_AT > ?", key, now)
                .end();
        return jdbcTemplate.executeUpdate(incr);
    }

    private long conditionalDelete(String key, long now) {
        SQL del = SQL.begin().name("emailCodeConsume").querySpace(DEFAULT_QUERY_SPACE)
                .sql("DELETE FROM " + TABLE + " WHERE CODE_KEY = ? AND EXPIRE_AT > ?", key, now).end();
        return jdbcTemplate.executeUpdate(del);
    }

    private void deleteByKey(String key) {
        SQL del = SQL.begin().name("emailCodeDelete").querySpace(DEFAULT_QUERY_SPACE)
                .sql("DELETE FROM " + TABLE + " WHERE CODE_KEY = ?", key).end();
        jdbcTemplate.executeUpdate(del);
    }

    private int readFailCount(String key) {
        SQL select = SQL.begin().name("emailCodeFailCount").querySpace(DEFAULT_QUERY_SPACE)
                .sql("SELECT FAIL_COUNT FROM " + TABLE + " WHERE CODE_KEY = ?", key).end();
        Integer val = jdbcTemplate.findInt(select, null);
        return val == null ? 0 : val;
    }
}
