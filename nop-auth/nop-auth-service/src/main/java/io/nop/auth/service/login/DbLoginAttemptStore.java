/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.core.login.ILoginAttemptStore;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.dao.entity.NopAuthLoginAttempt;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoErrors;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

import jakarta.inject.Inject;

/**
 * {@link ILoginAttemptStore} 的 DB 实现（design nop-auth §3.2 db 增补，plan 2275）——
 * 无 Redis 部署经 `nop.auth.login-attempt.store-type=db` 获得集群锁号（表
 * `nop_auth_login_attempt`：PK `attempt_key` = `un:`/`ip:` 前缀键）。
 * <p>
 * <b>原子递增</b> = 条件 UPDATE（`fail_count=fail_count+1 WHERE attempt_key=? AND
 * expire_at&gt;now`，SQL 原子——read-modify-write 原子性由单语句承接）；affected=0（不存在
 * <b>或已过期</b>）→ 条件 DELETE 过期行 + INSERT(fail_count=1, expire_at=now+TTL)；INSERT 撞
 * PK（并发竞态）→ 重试条件 UPDATE（`DbSmsCodeStore.send` 同款回退先例）。
 * <p>
 * TTL：注入 {@link UserContextConfig#getLoginFailTimeout()}；固定窗口——首次 INSERT 定值，
 * 递增不刷新（对齐 {@code RedisLoginAttemptStore}，与 Local 滑动窗口的既有跨后端分叉在
 * plan 2275 审查 M5 已裁定为显式选择）。get 过期读 0 + 惰性删除；set 覆盖 upsert；reset DELETE。
 */
public class DbLoginAttemptStore implements ILoginAttemptStore {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    UserContextConfig config;

    private IEntityDao<NopAuthLoginAttempt> dao() {
        return daoProvider.daoFor(NopAuthLoginAttempt.class);
    }

    private long ttlMillis() {
        return config.getLoginFailTimeout().toMillis();
    }

    @Override
    public int getLoginFailCount(String key) {
        return selectCount(key, CoreMetrics.currentTimeMillis());
    }

    @Override
    public void setLoginFailCount(String key, int count) {
        long now = CoreMetrics.currentTimeMillis();
        NopAuthLoginAttempt existing = freshRow(key, now);
        if (existing != null) {
            existing.setFailCount(count);
            existing.setExpireAt(now + ttlMillis());
            dao().updateEntityDirectly(existing);
        } else {
            deleteExpired(key, now);
            try {
                insertAttempt(key, count, now + ttlMillis());
            } catch (NopException e) {
                if (!DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(e.getErrorCode()))
                    throw e;
                NopAuthLoginAttempt row = freshRow(key, now);
                if (row == null)
                    throw e;
                row.setFailCount(count);
                row.setExpireAt(now + ttlMillis());
                dao().updateEntityDirectly(row);
            }
        }
    }

    @Override
    public void resetLoginFailCount(String key) {
        // 无条件删除（含未过期行——清零语义）
        SQL del = SQL.begin().name("authLoginAttemptReset")
                .sql("delete from NopAuthLoginAttempt o where o.attemptKey = ?", key)
                .end();
        ormTemplate.executeUpdate(del);
    }

    @Override
    public int incrementLoginFailCount(String key) {
        long now = CoreMetrics.currentTimeMillis();
        long affected = incrementIfFresh(key, now);
        if (affected > 0) {
            return selectCount(key, now);
        }
        // 行不存在或已过期：删过期行（惰性清理）+ 新窗口插入
        deleteExpired(key, now);
        try {
            insertAttempt(key, 1, now + ttlMillis());
        } catch (NopException e) {
            if (!DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(e.getErrorCode()))
                throw e;
            // 并发竞态：他方已插入新鲜行 → 重试条件递增（仍 0 = 兜底再删再插）
            if (incrementIfFresh(key, now) > 0) {
                return selectCount(key, now);
            }
            deleteExpired(key, now);
            insertAttempt(key, 1, now + ttlMillis());
        }
        return 1;
    }

    private long incrementIfFresh(String key, long now) {
        SQL upd = SQL.begin().name("authLoginAttemptIncrement")
                .sql("update NopAuthLoginAttempt o set o.failCount = o.failCount + 1 "
                        + "where o.attemptKey = ? and o.expireAt > ?", key, now)
                .end();
        return ormTemplate.executeUpdate(upd);
    }

    private long deleteExpired(String key, long now) {
        SQL del = SQL.begin().name("authLoginAttemptDeleteExpired")
                .sql("delete from NopAuthLoginAttempt o where o.attemptKey = ? and o.expireAt <= ?", key, now)
                .end();
        return ormTemplate.executeUpdate(del);
    }

    private int selectCount(String key, long now) {
        NopAuthLoginAttempt row = freshRow(key, now);
        return row == null || row.getFailCount() == null ? 0 : row.getFailCount();
    }

    private NopAuthLoginAttempt freshRow(String key, long now) {
        NopAuthLoginAttempt row = dao().getEntityById(key);
        if (row == null)
            return null;
        if (row.getExpireAt() != null && row.getExpireAt() <= now) {
            deleteExpired(key, now); // 惰性清理过期行
            return null;
        }
        return row;
    }

    private void insertAttempt(String key, int count, long expireAt) {
        NopAuthLoginAttempt e = dao().newEntity();
        e.setAttemptKey(key);
        e.setFailCount(count);
        e.setExpireAt(expireAt);
        dao().saveEntityDirectly(e);
    }
}
