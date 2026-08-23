/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import io.nop.auth.dao.entity.NopAuthSmsCode;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

import jakarta.inject.Inject;


/**
 * {@link SmsCodeStore} 的数据库实现（设计 §3.3，store-type=db，W8 默认）。
 * <p>
 * 三态语义与 {@code LocalSmsCodeStore}/{@code RedisSmsCodeStore} 一致：
 * <ul>
 *   <li>{@code send}：6 位随机码 + INSERT（重发为 upsert：覆盖旧码 + 重置 failCount）。</li>
 *   <li>{@code verify}：三态——
 *     <ul>
 *       <li>VALID（匹配+未过期）：条件 DELETE 一次性消费（affected&gt;0 才判 VALID）。</li>
 *       <li>EXPIRED（不存在/已过期）：过期则惰性删除。</li>
 *       <li>MISMATCH（存在+未过期但不匹配）：SQL 原子递增 failCount，达 max-attempts 作废。</li>
 *     </ul>
 *   </li>
 *   <li>{@code consume}：显式 DELETE。</li>
 * </ul>
 * 存储明文 code + 短 TTL（裁决：对齐 Local/Redis 实现的行为等价；5min TTL + max-attempts
 * 防爆破已足够；BCrypt 收益对瞬态数据不显著）。安全裁决见
 * {@code ai-dev/design/nop-auth/01-architecture-baseline.md} §3.3（W8 回写）。
 */
public class DbSmsCodeStore implements SmsCodeStore {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private SmsCodeStoreConfig config = new SmsCodeStoreConfig();

    public void setConfig(SmsCodeStoreConfig config) {
        this.config = config;
    }

    private IEntityDao<NopAuthSmsCode> dao() {
        return daoProvider.daoFor(NopAuthSmsCode.class);
    }

    private long ttlMillis() {
        return config.getExpireSeconds() * 1000L;
    }

    @Override
    public String send(String key) {
        long now = System.currentTimeMillis();
        String code = String.format("%06d", MathHelper.secureRandom().nextInt(1_000_000));
        NopAuthSmsCode existing = dao().getEntityById(key);
        if (existing != null) {
            // 重发：覆盖旧码 + 重置失败计数（新周期）
            existing.setCode(code);
            existing.setExpireAt(now + ttlMillis());
            existing.setFailCount(0);
            dao().updateEntityDirectly(existing);
        } else {
            NopAuthSmsCode e = dao().newEntity();
            e.setCodeKey(key);
            e.setPhone(extractPhone(key));
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
        NopAuthSmsCode e = dao().getEntityById(key);
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
        SQL incr = SQL.begin().name("smsCodeIncrFail")
                .sql("update NopAuthSmsCode o set o.failCount = o.failCount + 1 where o.codeKey = ? and o.expireAt > ?", key, now)
                .end();
        return ormTemplate.executeUpdate(incr);
    }

    private long conditionalDelete(String key, long now) {
        SQL del = SQL.begin().name("smsCodeConsume")
                .sql("delete from NopAuthSmsCode o where o.codeKey = ? and o.expireAt > ?", key, now).end();
        return ormTemplate.executeUpdate(del);
    }

    private void deleteByKey(String key) {
        SQL del = SQL.begin().name("smsCodeDelete")
                .sql("delete from NopAuthSmsCode o where o.codeKey = ?", key).end();
        ormTemplate.executeUpdate(del);
    }

    private int readFailCount(String key) {
        SQL select = SQL.begin().name("smsCodeFailCount")
                .sql("select o.failCount from NopAuthSmsCode o where o.codeKey = ?", key).end();
        Integer val = ormTemplate.findInt(select, null);
        return val == null ? 0 : val;
    }

    private static String extractPhone(String key) {
        if (key != null && key.startsWith("login:"))
            return key.substring("login:".length());
        return null;
    }
}
