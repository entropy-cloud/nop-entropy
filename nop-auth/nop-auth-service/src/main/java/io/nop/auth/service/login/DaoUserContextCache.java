/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.commons.util.DateHelper;
import io.nop.commons.concurrent.lock.IResourceLockManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Dao 部署的会话上下文缓存（2.3-C1-010，plan 2568 修复）。
 *
 * <p>flush 语义：saveUserContextAsync 改为 per-session 单飞（{@link IResourceLockManager}）内的
 * EQL 条件 UPDATE——乐观条件 = {@link #getUserContextAsync(String)} 读时捕获的 DB 行基线
 * （refreshToken/lastAccessTime）+ logoutType 守卫。affected-row=0（行不存在/已登出/基线失配）
 * 时放弃写入并告警；仅落库成功后 clearDirty。过期副本不再回滚已旋转的 token。
 *
 * <p>行为变更声明：良性并发下后到 flush（同基线但首个 flush 已推进 DB）整体放弃 = 该请求内
 * 非 token 字段修改（nickName/roles 等 cacheData 内容）本次不落库。副本为 request-scoped、
 * 下请求重读 DB，丢失窗口为单请求内变更；fail-safe（DB 中 token 权威）优先于 last-writer-wins。
 */
public class DaoUserContextCache extends LocalUserContextCache {
    static final Logger LOG = LoggerFactory.getLogger(DaoUserContextCache.class);

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IResourceLockManager lockManager;

    /**
     * 锁持有者实例级标识（同 JVM lock/unlock 配对语义，平台 AlertEvaluator 先例形态）。
     */
    private final String lockHolderId = "dao-user-context-cache@" + UUID.randomUUID();

    private IEntityDao<NopAuthSession> dao() {
        return daoProvider.daoFor(NopAuthSession.class);
    }

    /**
     * 携带 flush 乐观基线的副本类型。基线为普通 Java 字段：{@code serializeToJson} 白名单
     * 序列化不含子类字段，因此不会泄漏进 NopAuthSession.cacheData。
     */
    static class DaoUserContext extends UserContextImpl {
        /**
         * 读时 DB 行 refreshToken 值（getUserContextAsync :79 读时快照）。
         */
        String baselineRefreshToken;
        /**
         * 读时 DB 行 lastAccessTime 值（加载实体的 DB 行值，不是副本上的本请求时刻值）。
         */
        LocalDateTime baselineLastAccessTime;
    }

    @SingleSession
    @Override
    public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
        Guard.notEmpty(sessionId, "sessionId");

        return FutureHelper.futureCall(() -> {
            NopAuthSession session = dao().getEntityById(sessionId);
            if (session == null) {
                LOG.debug("nop.auth.login-session-not-exists:sessionId={}", sessionId);
                return null;
            }

            if (session.getLogoutType() != AuthApiConstants.LOGOUT_TYPE_NONE) {
                LOG.debug("nop.auth.login-session-already-logout:sessionId={}", sessionId);
                return null;
            }

            if (checkExpired(session))
                return null;

            DaoUserContext userContext = new DaoUserContext();

            Map<String, Object> map = (Map<String, Object>) JsonTool.parse(session.getCacheData());
            if (map != null) {
                BeanTool.instance().setProperties(userContext, map);
            }

            userContext.setSessionId(session.getSessionId());
            userContext.setUserId(session.getUserId());
            userContext.setUserName(session.getUserName());
            userContext.setTenantId(session.getTenantId());
            userContext.setLoginType(session.getLoginType());
            userContext.setAccessToken(session.getAccessToken());
            userContext.setRefreshToken(session.getRefreshToken());

            // 乐观基线：读时 DB 行值（refreshToken 取本实体读时值，lastAccessTime 取 DB 行值，
            // 而非下方 setLastAccessTime 的本请求时刻——后者用作条件会导致全量 flush 失配）
            userContext.baselineRefreshToken = session.getRefreshToken();
            userContext.baselineLastAccessTime = session.getLastAccessTime();

            userContext.setLastAccessTime(CoreMetrics.currentTimeMillis());
            return userContext;
        });

    }

    protected boolean checkExpired(NopAuthSession session) {
        LocalDateTime lastAccessTime = session.getLastAccessTime();
        if (lastAccessTime == null) {
            // check2 P2 修复：裸 CrudBizModel 直写/历史遗留行可能无 lastAccessTime（非 mandatory 列），
            // 按"已过期/非法行"fail-closed 处理（返回 null），不得在会话校验路径 NPE
            LOG.warn("nop.auth.login-session-missing-last-access-time:sessionId={}", session.getSessionId());
            return true;
        }

        LocalDateTime now = CoreMetrics.currentDateTime();
        LocalDateTime timeoutTime = lastAccessTime.plus(config.getSessionTimeout());


        if (now.isAfter(timeoutTime)) {
            // 2.3-C1-010 F3②（plan 2568）：过期标记显式条件 UPDATE 持久化（仅未登出行生效），
            // 不再依赖加载实体改写随会话隐式落库
            SQL upd = SQL.begin().name("craSessionMarkExpired")
                    .sql("update NopAuthSession o set o.logoutType = ?, o.logoutTime = ? "
                            + "where o.sessionId = ? and o.logoutType = ?",
                            AuthApiConstants.LOGOUT_TYPE_EXPIRE, CoreMetrics.currentTimestamp(),
                            session.getSessionId(), AuthApiConstants.LOGOUT_TYPE_NONE)
                    .end();
            ormTemplate.executeUpdate(upd);
            return true;
        }

        return false;
    }

    @SingleSession
    @Override
    public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
        Guard.notNull(userContext, "userContext");

        return FutureHelper.futureRun(() -> {
            String sessionId = userContext.getSessionId();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("nickName", userContext.getNickName());
            map.put("locale", userContext.getLocale());
            map.put("timeZone", userContext.getTimeZone());
            map.put("openId", userContext.getOpenId());
            if (userContext.getPrimaryRole() != null)
                map.put("primaryRole", userContext.getPrimaryRole());
            map.put("roles", userContext.getRoles());
            map.put("deptId", userContext.getDeptId());
            map.put("deptName", userContext.getDeptName());
            // MFA 受限会话标志（W13-impl Dao-cache 白名单触点，设计 §4.3 持久化机制）：
            // 必须纳入序列化白名单，否则 Dao-cache 部署下第二请求起标志丢失 = fail-open。
            // 仅受限会话写入（正常会话 cacheData 形态零变化；缺失键反序列化为缺省 false）。
            if (userContext.isMfaRestricted()) {
                map.put("mfaRestricted", Boolean.TRUE);
            }
            if (userContext.getAttrs() != null) {
                map.put("attrs", userContext.getAttrs());
            }

            String cacheData = JsonTool.stringify(map);
            LocalDateTime lastAccessTime = DateHelper.millisToDateTime(userContext.getLastAccessTime());

            try {
                // 2.3-C1-010（plan 2568）：per-session 单飞 + 基线条件 UPDATE，
                // 过期副本不再回滚已旋转 token；AuthHttpServerFilter 丢弃 flush 返回值，
                // 失败观测面即本方法日志
                lockManager.callWithLock(FLUSH_LOCK_RESOURCE_PREFIX + sessionId, lockHolderId,
                        FLUSH_LOCK_REASON, lock -> {
                            executeConditionalFlush(sessionId, userContext, cacheData, lastAccessTime);
                            return null;
                        });
            } catch (Throwable e) {
                LOG.error("nop.auth.session-flush-fail:sessionId={}", sessionId, e);
                throw io.nop.api.core.exceptions.NopException.adapt(e);
            }
        });
    }

    private void executeConditionalFlush(String sessionId, IUserContext userContext, String cacheData,
                                         LocalDateTime lastAccessTime) {
        String baselineRefreshToken = null;
        LocalDateTime baselineLastAccessTime = null;
        if (userContext instanceof DaoUserContext) {
            DaoUserContext daoContext = (DaoUserContext) userContext;
            baselineRefreshToken = daoContext.baselineRefreshToken;
            baselineLastAccessTime = daoContext.baselineLastAccessTime;
        }

        SQL upd;
        if (baselineRefreshToken != null && baselineLastAccessTime != null) {
            // 基线条件 UPDATE：仅当 DB 行仍与本副本读时快照一致时生效
            upd = SQL.begin().name("craSessionFlushBaseline")
                    .sql("update NopAuthSession o set o.accessToken = ?, o.refreshToken = ?, "
                            + "o.cacheData = ?, o.lastAccessTime = ? "
                            + "where o.sessionId = ? and o.refreshToken = ? and o.lastAccessTime = ? "
                            + "and o.logoutType = ?",
                            userContext.getAccessToken(), userContext.getRefreshToken(), cacheData,
                            lastAccessTime, sessionId, baselineRefreshToken, baselineLastAccessTime,
                            AuthApiConstants.LOGOUT_TYPE_NONE)
                    .end();
        } else {
            // 基线缺失（登录路径等非本 cache 创建的上下文）：退化为主键 + logout 守卫 UPDATE
            //（不得无条件——logout 后迟到 flush 不得复活已登出行）
            upd = SQL.begin().name("craSessionFlushGuarded")
                    .sql("update NopAuthSession o set o.accessToken = ?, o.refreshToken = ?, "
                            + "o.cacheData = ?, o.lastAccessTime = ? "
                            + "where o.sessionId = ? and o.logoutType = ?",
                            userContext.getAccessToken(), userContext.getRefreshToken(), cacheData,
                            lastAccessTime, sessionId, AuthApiConstants.LOGOUT_TYPE_NONE)
                    .end();
        }

        long affected = ormTemplate.executeUpdate(upd);
        if (affected == 0) {
            if (isRowAbsent(sessionId)) {
                // 行尚未落库：登录路径在调用方 orm 会话内 saveSession（insert 待会话 flush），
                // 本 EQL UPDATE 查不到 DB 行。回退实体改写——该行未提交前对其他写者不可见
                //（单写者），无丢失更新面；随调用方会话一并提交。
                flushViaLoadedEntity(sessionId, userContext, cacheData, lastAccessTime);
            } else {
                // 行存在但基线失配/已登出（其他副本已推进 DB，本副本过期）：放弃写入
                LOG.warn("nop.auth.session-flush-stale-abandoned:sessionId={}", sessionId);
            }
        } else {
            // 仅落库成功后 clearDirty（clearDirty 先于持久化是 C1-010 失效面③共因）
            userContext.clearDirty();
        }
    }

    private boolean isRowAbsent(String sessionId) {
        SQL cnt = SQL.begin().name("craSessionFlushRowExists")
                .sql("select count(*) from NopAuthSession o where o.sessionId = ?", sessionId)
                .end();
        return ormTemplate.findInt(cnt, 0) == 0;
    }

    private void flushViaLoadedEntity(String sessionId, IUserContext userContext, String cacheData,
                                      LocalDateTime lastAccessTime) {
        NopAuthSession session = dao().getEntityById(sessionId);
        if (session == null || session.getLogoutType() != AuthApiConstants.LOGOUT_TYPE_NONE) {
            LOG.warn("nop.auth.session-flush-stale-abandoned:sessionId={}", sessionId);
            return;
        }
        session.setAccessToken(userContext.getAccessToken());
        session.setRefreshToken(userContext.getRefreshToken());
        session.setCacheData(cacheData);
        session.setLastAccessTime(lastAccessTime);
        // 随调用方 orm 会话提交；提交前对其他写者不可见，clearDirty 此处安全
        userContext.clearDirty();
    }

    @Override
    public CompletionStage<String> getUserSessionId(String userName) {
        Guard.notEmpty(userName, "userName");

        return FutureHelper.futureCall(() -> {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(NopAuthSession.PROP_NAME_userName, userName))
                    .addFilter(FilterBeans.eq(NopAuthSession.PROP_NAME_logoutType, AuthApiConstants.LOGOUT_TYPE_NONE));
            query.addOrderField(NopAuthSession.PROP_NAME_loginTime, true);
            NopAuthSession session = dao().findFirstByQuery(query);
            return session == null ? null : session.getSessionId();
        });
    }

    @Override
    public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
        // 不需要删除
        return FutureHelper.success(null);
    }

    static final String FLUSH_LOCK_RESOURCE_PREFIX = "auth:session-flush:";
    static final String FLUSH_LOCK_REASON = "dao-user-context-flush";
}
