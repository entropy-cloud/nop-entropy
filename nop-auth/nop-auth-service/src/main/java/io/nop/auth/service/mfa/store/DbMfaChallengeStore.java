/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.MfaChallengeStoreConfig;
import io.nop.auth.dao.entity.NopAuthMfaChallenge;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

import jakarta.inject.Inject;


/**
 * {@link MfaChallengeStore} 的数据库实现（设计 §3.3，store-type=db，W8 默认）。
 * <p>
 * 语义与 {@code LocalMfaChallengeStore}/{@code RedisMfaChallengeStore} 一致：
 * <ul>
 *   <li>{@code create}：INSERT，{@code expireAt} = now + TTL。</li>
 *   <li>{@code peek}：SELECT（<b>不刷新 TTL</b>），过期则惰性 DELETE 返 null。</li>
 *   <li>{@code incrFailCount}：SQL 原子递增 {@code FAIL_COUNT = FAIL_COUNT + 1}（对齐 Redis INCRBY），
 *       仅当未过期时生效，返回递增后的值。</li>
 *   <li>{@code consume}：条件 DELETE（{@code WHERE token=? AND expire_at>now}）保证一次性，
 *       返回被删行（捕获自 SELECT）。</li>
 * </ul>
 * 原子性：单行语句天然原子；{@code consume} 用条件 DELETE + affected-row 判定实现一次性
 * （并发 consume 仅一个成功）。TTL 清理为惰性：peek/consume 时过期即删；批量清理为 Follow-up。
 * <p>
 * 事务边界：单语句操作无需显式事务（DB 单语句原子）。
 * <p>
 * 语句载体为 EQL（plan 2257 平移，原 raw SQL）：实体短名 + 属性名经 {@code ormTemplate} 编译执行；
 * 标量读直查 DB（绕过一级缓存，{@code markVerified} 等 bulk 写后读到提交值）。
 */
public class DbMfaChallengeStore implements MfaChallengeStore {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    private MfaChallengeStoreConfig config = new MfaChallengeStoreConfig();

    public void setConfig(MfaChallengeStoreConfig config) {
        this.config = config;
    }

    private IEntityDao<NopAuthMfaChallenge> dao() {
        return daoProvider.daoFor(NopAuthMfaChallenge.class);
    }

    private long ttlMillis() {
        return config.getExpireSeconds() * 1000L;
    }

    @Override
    public String create(String scene, String userId, String mfaType, int loginType, String tenantId, String phone,
                         String payload) {
        long now = System.currentTimeMillis();
        String token = StringHelper.generateUUID();
        NopAuthMfaChallenge e = dao().newEntity();
        e.setChallengeToken(token);
        e.setUserId(userId);
        e.setMfaType(mfaType);
        e.setLoginType(loginType);
        e.setTenantId(tenantId);
        e.setPhone(phone);
        e.setExpireAt(now + ttlMillis());
        e.setFailCount(0);
        e.setScene(scene);
        e.setPayload(payload);
        dao().saveEntityDirectly(e);
        return token;
    }

    @Override
    public MfaChallenge peek(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return null;
        NopAuthMfaChallenge e = dao().getEntityById(challengeToken);
        if (e == null)
            return null;
        long now = System.currentTimeMillis();
        // peek 不刷新 TTL —— expireAt 在 create 时固化
        if (e.getExpireAt() != null && e.getExpireAt() <= now) {
            deleteByToken(challengeToken); // 惰性 TTL 清理
            return null;
        }
        // 票窗口判定（设计 §3.3）：已验证票仅在 verifiedAt + op-ticket-expire 内可见。
        // verifiedAt 经 EQL 标量投影读取（直查 DB 绕过一级缓存，markVerified 为 bulk 写）
        Long verifiedAt = readVerifiedAt(challengeToken);
        if (verifiedAt != null && now >= verifiedAt + opTicketMillis()) {
            deleteByToken(challengeToken); // 票失效即 challenge 整体失效（票不续命）
            return null;
        }
        MfaChallenge pojo = toPojo(e);
        pojo.setVerifiedAt(verifiedAt);
        return pojo;
    }

    @Override
    public int incrFailCount(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return 0;
        long now = System.currentTimeMillis();
        // EQL 原子递增（对齐 Redis INCRBY），仅未过期行生效
        SQL incr = SQL.begin().name("mfaChallengeIncrFail")
                .sql("update NopAuthMfaChallenge o set o.failCount = o.failCount + 1 "
                        + "where o.challengeToken = ? and o.expireAt > ?", challengeToken, now)
                .end();
        long affected = ormTemplate.executeUpdate(incr);
        if (affected == 0)
            return 0; // 不存在或已过期，计数无意义（对齐 Local 语义）
        // 读回递增后的值（EQL 标量投影直查 DB，绕过一级缓存避免读到脏值）
        return readFailCount(challengeToken);
    }

    @Override
    public MfaChallenge consume(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return null;
        NopAuthMfaChallenge e = dao().getEntityById(challengeToken);
        if (e == null)
            return null;
        if (e.getExpireAt() != null && e.getExpireAt() <= System.currentTimeMillis()) {
            deleteByToken(challengeToken); // 惰性清理过期行
            return null;
        }
        // 捕获数据后条件 DELETE：一次性语义（并发 consume 仅一个 DELETE 影响行>0）。
        // consume 一次性不因 markVerified 改变：条件仅校验 EXPIRE_AT（票状态不拦截消费）
        MfaChallenge captured = toPojo(e);
        captured.setVerifiedAt(readVerifiedAt(challengeToken));
        long now = System.currentTimeMillis();
        SQL del = SQL.begin().name("mfaChallengeConsume")
                .sql("delete from NopAuthMfaChallenge o where o.challengeToken = ? and o.expireAt > ?",
                        challengeToken, now)
                .end();
        long affected = ormTemplate.executeUpdate(del);
        return affected > 0 ? captured : null;
    }

    @Override
    public boolean markVerified(String challengeToken) {
        if (StringHelper.isEmpty(challengeToken))
            return false;
        // 条件 UPDATE + affected-row：仅首个验证者迁移成功（并发恰一次，设计 §3.3 原子性契约）
        long now = System.currentTimeMillis();
        SQL upd = SQL.begin().name("mfaChallengeMarkVerified")
                .sql("update NopAuthMfaChallenge o set o.verifiedAt = ? "
                        + "where o.challengeToken = ? and o.verifiedAt is null and o.expireAt > ?",
                        now, challengeToken, now)
                .end();
        return ormTemplate.executeUpdate(upd) > 0;
    }

    private long opTicketMillis() {
        return config.getOpTicketExpireSeconds() * 1000L;
    }

    private void deleteByToken(String token) {
        SQL del = SQL.begin().name("mfaChallengeDelete")
                .sql("delete from NopAuthMfaChallenge o where o.challengeToken = ?", token).end();
        ormTemplate.executeUpdate(del);
    }

    private int readFailCount(String token) {
        SQL select = SQL.begin().name("mfaChallengeFailCount")
                .sql("select o.failCount from NopAuthMfaChallenge o where o.challengeToken = ?", token).end();
        Integer val = ormTemplate.findInt(select, null);
        return val == null ? 0 : val;
    }

    /** EQL 标量投影读 verifiedAt（null=未验证；直查 DB 绕过一级缓存，bulk 写后读到提交值）。 */
    private Long readVerifiedAt(String token) {
        SQL select = SQL.begin().name("mfaChallengeVerifiedAt")
                .sql("select o.verifiedAt from NopAuthMfaChallenge o where o.challengeToken = ?", token).end();
        Long val = ormTemplate.findLong(select, null);
        return val;
    }

    private static MfaChallenge toPojo(NopAuthMfaChallenge e) {
        long now = System.currentTimeMillis();
        long created = e.getCreateTime() != null ? e.getCreateTime().getTime() : now;
        long expireAt = e.getExpireAt() != null ? e.getExpireAt() : 0L;
        int loginType = e.getLoginType() != null ? e.getLoginType() : 0;
        MfaChallenge pojo = new MfaChallenge(e.getChallengeToken(), e.getUserId(), e.getMfaType(), loginType,
                e.getTenantId(), e.getPhone(), created, expireAt);
        pojo.setScene(e.getScene());
        pojo.setPayload(e.getPayload());
        return pojo;
    }
}
