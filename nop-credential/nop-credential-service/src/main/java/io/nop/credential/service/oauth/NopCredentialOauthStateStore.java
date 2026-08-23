/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.oauth;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.credential.dao.entity.NopCredentialOauthState;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;


/**
 * OAuth state 绑定的 DB 存储（W9 Phase 1 Decision：新 ORM 实体，跨副本可读）。
 *
 * <p>语义（W8 {@code DbMfaChallengeStore} 同构）：
 * <ul>
 *   <li>{@code create}：INSERT（state = 安全随机 128bit hex），{@code expireAt} = now + TTL；
 *       插入前惰性清理全表过期行（DELETE WHERE EXPIRE_AT &lt; now，无后台任务）。</li>
 *   <li>{@code peek}：按 PK 读行（不做 TTL 判定，由调用方与 consume 条件一致判定）。</li>
 *   <li>{@code consume}：条件 UPDATE（{@code WHERE STATE=? AND CONSUMED=0 AND EXPIRE_AT>now}）
 *       + affected-row 判定实现一次性原子消费（并发双回调恰一个成功）；行保留供审计。</li>
 * </ul>
 */
@Singleton
public class NopCredentialOauthStateStore {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    private IEntityDao<NopCredentialOauthState> dao() {
        return daoProvider.daoFor(NopCredentialOauthState.class);
    }

    /**
     * 生成不可预测的一次性 state（安全随机 ≥128 bit，{@link StringHelper#generateUUID(int)}
     * 内部走 {@code MathHelper.secureRandom()}）。
     */
    public static String newStateToken() {
        return StringHelper.generateUUID(16); // 16 bytes = 128 bit → 32 hex chars
    }

    /**
     * 持久化 state 绑定（credentialId + 发起人 + TTL），返回 state 令牌。
     * 插入前惰性清理过期行（无后台任务）。
     */
    public String create(String credentialId, String userId, long ttlSeconds) {
        cleanupExpired();

        long now = System.currentTimeMillis();
        String state = newStateToken();

        NopCredentialOauthState entity = dao().newEntity();
        entity.setState(state);
        entity.setCredentialId(credentialId);
        entity.setUserId(userId);
        entity.setExpireAt(now + ttlSeconds * 1000L);
        entity.setConsumed((byte) 0);
        entity.setCreateTime(new java.sql.Timestamp(now));
        entity.setCreatedBy(userId);
        dao().saveEntityDirectly(entity);
        return state;
    }

    /**
     * 按 PK 读 state 绑定行。未命中返回 null（调用方统一按 state-invalid fail-closed）。
     */
    public NopCredentialOauthState peek(String state) {
        if (StringHelper.isEmpty(state))
            return null;
        return dao().getEntityById(state);
    }

    /**
     * 一次性原子消费：条件 UPDATE（未消费且未过期才置 CONSUMED=1），
     * affected-row 判定（0 行 = 未命中/过期/重放，统一 fail-closed）。
     */
    public boolean consume(String state) {
        if (StringHelper.isEmpty(state))
            return false;
        long now = System.currentTimeMillis();
        SQL update = SQL.begin().name("credentialOauthStateConsume")
                .sql("update NopCredentialOauthState o set o.consumed = 1 "
                        + "where o.state = ? and o.consumed = 0 and o.expireAt > ?", state, now)
                .end();
        long affected = ormTemplate.executeUpdate(update);
        return affected > 0;
    }

    /**
     * 惰性清理过期行（发起时触发；批量清理任务为 Non-Blocking Follow-up）。
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        SQL del = SQL.begin().name("credentialOauthStateCleanup")
                .sql("delete from NopCredentialOauthState o where o.expireAt < ?", now).end();
        ormTemplate.executeUpdate(del);
    }
}
