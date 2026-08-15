package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.service.NopDatavDashboardOwnerGuard;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.share.NopDatavShareAccessGuard;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_ENABLED;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_RETRY_AFTER_SECONDS;
import static io.nop.datav.service.NopDatavErrors.ARG_SHARE_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_SHARE_TOKEN;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DISABLED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_EXPIRED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_LOCKED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_MISMATCH;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_REQUIRED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_RATE_LIMITED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_TOKEN_GENERATE_FAILED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_TOKEN_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_NOT_FOUND;

/**
 * 看板分享管理 + 公共访问 BizModel（D3-2）。
 *
 * <p>管理 action（create/list/revoke/toggle）：经 {@code @Auth} + Dashboard owner 校验
 * （{@link NopDatavDashboardOwnerGuard}）限定仅看板 owner/admin 可管理分享。</p>
 *
 * <p>公共访问 action（{@code getSharedDashboard}）：{@code @Auth(publicAccess=true)} 匿名放行，
 * 校验 token + 密码 + 有效期 + 启用后直接经 DAO 读已发布快照（不经 RLS/requireEntity）。</p>
 */
@BizModel("NopDatavDashboardShare")
public class NopDatavDashboardShareBizModel extends CrudBizModel<NopDatavDashboardShare>
        implements INopDatavDashboardShareBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavDashboardShareBizModel.class);

    /**
     * 分享令牌重试上限（生成不可枚举随机串时的唯一性冲突重试）。
     */
    private static final int TOKEN_MAX_RETRIES = 8;

    /**
     * 启用标记（domain boolFlag）：1=启用，0=禁用。
     */
    private static final byte ENABLED_TRUE = 1;
    private static final byte ENABLED_FALSE = 0;

    @Inject
    protected IPasswordEncoder passwordEncoder;

    /**
     * 分享访问两级限流 guard（独立 bean 装配，时钟 seam 见 {@link NopDatavShareAccessGuard#setClock}）。
     */
    @Inject
    protected NopDatavShareAccessGuard shareAccessGuard;

    /**
     * 访问统计定向 SQL 用（R4：数据库端原子自增，不走实体 update）。
     */
    @Inject
    protected IJdbcTemplate jdbcTemplate;

    public NopDatavDashboardShareBizModel() {
        setEntityName(NopDatavDashboardShare.class.getName());
    }

    // ==================== 分享管理 API（owner/admin） ====================

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboardShare:createShare")
    public NopDatavDashboardShare createShare(@Name("dashboardId") String dashboardId,
                                              @Name("password") String password,
                                              @Name("expireTime") Timestamp expireTime,
                                              IServiceContext context) {
        NopDatavDashboardOwnerGuard.requireDashboardOwnership(daoProvider(), dashboardId, context);

        String operator = NopDatavOperatorResolver.resolveOperator(context);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        NopDatavDashboardShare share = daoProvider().daoFor(NopDatavDashboardShare.class).newEntity();
        share.setShareId(StringHelper.generateUUID());
        share.setShareToken(generateUniqueToken());
        share.setDashboardId(dashboardId);
        share.setPasswordHash(encodeSharePassword(password));
        share.setExpireTime(expireTime);
        share.setEnabled(ENABLED_TRUE);
        share.setDelFlag((byte) 0);
        share.setVersion(0L);
        share.setVisitCount(0L);
        share.setCreatedBy(operator);
        share.setCreateTime(now);
        share.setUpdatedBy(operator);
        share.setUpdateTime(now);

        daoProvider().daoFor(NopDatavDashboardShare.class).saveEntityDirectly(share);
        share.setPasswordHash(null);
        return share;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboardShare:listShares")
    public List<NopDatavDashboardShare> listShares(@Name("dashboardId") String dashboardId,
                                                    IServiceContext context) {
        NopDatavDashboardOwnerGuard.requireDashboardOwnership(daoProvider(), dashboardId, context);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("createTime", true);
        @SuppressWarnings("unchecked")
        List<NopDatavDashboardShare> shares = (List<NopDatavDashboardShare>)
                daoProvider().daoFor(NopDatavDashboardShare.class).findAllByQuery(query);

        List<NopDatavDashboardShare> sanitized = new ArrayList<>(shares.size());
        for (NopDatavDashboardShare share : shares) {
            share.setPasswordHash(null);
            sanitized.add(share);
        }
        return sanitized;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboardShare:revokeShare")
    public NopDatavDashboardShare revokeShare(@Name("shareId") String shareId, IServiceContext context) {
        return doToggleShare(shareId, false, context);
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboardShare:toggleShare")
    public NopDatavDashboardShare toggleShare(@Name("shareId") String shareId,
                                              @Name("enabled") boolean enabled,
                                              IServiceContext context) {
        return doToggleShare(shareId, enabled, context);
    }

    // ==================== 公共访问 API（匿名，publicAccess） ====================

    @Override
    @BizQuery
    @Auth(publicAccess = true)
    public NopDatavDashboardSnapshot getSharedDashboard(@Name("shareToken") String shareToken,
                                                        @Name("password") String password,
                                                        IServiceContext context) {
        // R5 裁定次序：限流先于 token 查库（被限流请求零 DB 消耗）
        String rateLimitKey = checkShareAccessRateLimit(shareToken, context);

        NopDatavDashboardShare share = findShareByToken(shareToken);
        if (share == null) {
            throw new NopException(ERR_DATAV_SHARE_TOKEN_NOT_FOUND).param(ARG_SHARE_TOKEN, shareToken);
        }
        if (share.getEnabled() == null || share.getEnabled() != ENABLED_TRUE) {
            throw new NopException(ERR_DATAV_SHARE_DISABLED).param(ARG_SHARE_TOKEN, shareToken);
        }
        if (share.getExpireTime() != null
                && !share.getExpireTime().after(new Timestamp(System.currentTimeMillis()))) {
            throw new NopException(ERR_DATAV_SHARE_EXPIRED).param(ARG_SHARE_TOKEN, shareToken);
        }
        verifySharePassword(share, password, rateLimitKey);
        requireDashboardAlive(share);
        NopDatavDashboardSnapshot snapshot = readLatestSnapshot(share.getDashboardId());
        recordShareVisit(share);
        return snapshot;
    }

    // ==================== 内部方法 ====================

    /**
     * 两级限流检查（R1–R6，裁定见 permission-sharing-design.md「访问限流与访问统计」）。
     *
     * <p>开关关闭时返回 null 且零记账（行为与加固前逐字节等价）。检查次序：总速率 →（零 DB）→ 密码锁定
     * （零 DB）→ 后续 token 查库。超限/锁定均快速失败抛专用错误码（含 token 与 retryAfterSeconds，
     * 不含敏感信息），被拒请求不触发任何查询（R5）。</p>
     *
     * @return 限流键（{@code shareToken|clientIp}，R1 组合键；无来源 IP 时退化为 token 单维占位），
     *         供密码失败/成功记账复用；限流关闭时返回 null
     */
    private String checkShareAccessRateLimit(String shareToken, IServiceContext context) {
        if (!CFG_DATAV_SHARE_RATE_LIMIT_ENABLED.get()) {
            return null;
        }
        String key = buildShareRateLimitKey(shareToken, context);
        long windowMillis = CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS.get() * 1000L;

        int maxAccess = CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW.get();
        if (!shareAccessGuard.tryAcquireAccess(key, maxAccess, windowMillis)) {
            throw new NopException(ERR_DATAV_SHARE_RATE_LIMITED)
                    .param(ARG_SHARE_TOKEN, shareToken)
                    .param(ARG_RETRY_AFTER_SECONDS, retryAfterSeconds(
                            shareAccessGuard.getWindowRemainingMillis(key, windowMillis)));
        }
        if (shareAccessGuard.isPasswordLocked(key)) {
            throw new NopException(ERR_DATAV_SHARE_PASSWORD_LOCKED)
                    .param(ARG_SHARE_TOKEN, shareToken)
                    .param(ARG_RETRY_AFTER_SECONDS, retryAfterSeconds(
                            shareAccessGuard.getLockedRemainingMillis(key)));
        }
        return key;
    }

    /**
     * R1 组合键：{@code shareToken + 来源IP}。来源经 {@link IServiceContext#getRequestClientIp()}
     * （读网关注入的 {@code nop-client-addr} 头，信任边界=网关）；取不到时退化为 token 单维
     * （IP 段占位 "-"，退化语义显式记录于 design doc）。
     */
    private String buildShareRateLimitKey(String shareToken, IServiceContext context) {
        String clientIp = context == null ? null : context.getRequestClientIp();
        return shareToken + "|" + (StringHelper.isEmpty(clientIp) ? "-" : clientIp);
    }

    private static long retryAfterSeconds(long remainingMillis) {
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    /**
     * 访问统计记账（R4，裁定见 permission-sharing-design.md「访问限流与访问统计」）：仅成功访问
     * （全链路校验通过且快照已读到）触发；定向 SQL 数据库端原子自增（并发零丢失更新），不走实体
     * update → 无乐观锁 version bump、不触碰审计列（version/updatedBy/updateTime 语义保留给管理操作，
     * 匿名统计不冒充管理操作者）。统计写失败记 WARN 不阻断访问返回（辅助遥测 fail-open，显式裁定）。
     */
    private void recordShareVisit(NopDatavDashboardShare share) {
        try {
            jdbcTemplate.executeUpdate(SQL.begin().name("updateShareVisitStats")
                    .sql("update NOP_DATAV_SHARE set VISIT_COUNT=COALESCE(VISIT_COUNT,0)+1,LAST_VISIT_TIME=")
                    .param(new Timestamp(System.currentTimeMillis()))
                    .sql(" where SHARE_ID=").param(share.getShareId()).end());
        } catch (Exception e) {
            LOG.warn("nop.datav.share.visit-stats-update-failed: shareId={}", share.getShareId(), e);
        }
    }

    private NopDatavDashboardShare doToggleShare(String shareId, boolean enabled, IServiceContext context) {
        IDaoProvider daoProvider = daoProvider();
        NopDatavDashboardShare share = daoProvider.daoFor(NopDatavDashboardShare.class).getEntityById(shareId);
        if (share == null) {
            throw new NopException(ERR_DATAV_SHARE_NOT_FOUND).param(ARG_SHARE_ID, shareId);
        }
        NopDatavDashboardOwnerGuard.requireDashboardOwnership(daoProvider, share.getDashboardId(), context);

        String operator = NopDatavOperatorResolver.resolveOperator(context);
        share.setEnabled(enabled ? ENABLED_TRUE : ENABLED_FALSE);
        share.setUpdatedBy(operator);
        share.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopDatavDashboardShare.class).updateEntityDirectly(share);
        share.setPasswordHash(null);
        return share;
    }

    private NopDatavDashboardShare findShareByToken(String shareToken) {
        if (shareToken == null || shareToken.isEmpty()) {
            return null;
        }
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("shareToken", shareToken));
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavDashboardShare.class).findFirstByQuery(query);
    }

    /**
     * 密码校验 + 限流记账（R3 失败专用计数）：不匹配 → 失败记账（达阈值即置锁，锁定时长=窗口）后抛
     * MISMATCH；匹配 → 成功清账。缺密码（PASSWORD_REQUIRED）不计失败（未消耗一次猜测）。
     * {@code rateLimitKey} 为 null（限流开关关闭）时零记账。
     */
    private void verifySharePassword(NopDatavDashboardShare share, String password, String rateLimitKey) {
        String passwordHash = share.getPasswordHash();
        if (passwordHash == null || passwordHash.isEmpty()) {
            return;
        }
        if (password == null || password.isEmpty()) {
            throw new NopException(ERR_DATAV_SHARE_PASSWORD_REQUIRED).param(ARG_SHARE_TOKEN, share.getShareToken());
        }
        if (!passwordEncoder.passwordMatches(null, null, password, passwordHash)) {
            if (rateLimitKey != null) {
                shareAccessGuard.recordPasswordFailure(rateLimitKey,
                        CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES.get(),
                        CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS.get() * 1000L);
            }
            throw new NopException(ERR_DATAV_SHARE_PASSWORD_MISMATCH).param(ARG_SHARE_TOKEN, share.getShareToken());
        }
        if (rateLimitKey != null) {
            shareAccessGuard.recordPasswordSuccess(rateLimitKey);
        }
    }

    private String encodeSharePassword(String password) {
        if (password == null || password.isEmpty()) {
            return null;
        }
        return passwordEncoder.encodePassword(null, null, password);
    }

    /**
     * 生成全局唯一的分享令牌（{@link StringHelper#generateUUID()}）。唯一冲突时重试，
     * 超过 {@link #TOKEN_MAX_RETRIES} 仍冲突则显式失败（非静默返回重复令牌）。
     */
    private String generateUniqueToken() {
        IDaoProvider daoProvider = daoProvider();
        for (int i = 0; i < TOKEN_MAX_RETRIES; i++) {
            String token = StringHelper.generateUUID();
            if (findShareByToken(token) == null) {
                return token;
            }
        }
        throw new NopException(ERR_DATAV_SHARE_TOKEN_GENERATE_FAILED);
    }

    /**
     * 看板存活防御（defense-in-depth，plan 2026-08-14-2020-1）：token/enabled/expire/password 校验
     * 通过后、读取快照前，经 DAO 校验看板主表行存活。看板已删（级联吊销之外的任何残留路径）时显式拒绝
     * {@link NopDatavErrors#ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND}，不得静默返回旧快照。经 DAO 直读，
     * 不经 requireEntity/RLS（匿名上下文不适用，与 readLatestSnapshot 同约定）。
     */
    private void requireDashboardAlive(NopDatavDashboardShare share) {
        NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class)
                .getEntityById(share.getDashboardId());
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND)
                    .param(ARG_SHARE_TOKEN, share.getShareToken())
                    .param(ARG_DASHBOARD_ID, share.getDashboardId());
        }
    }

    /**
     * 直接经 DAO 按 dashboardId、snapshotVersion DESC 取首条已发布快照。
     * 不调用 {@code getPublishedDashboard}、不经 {@code requireEntity}/RLS（匿名上下文不适用）。
     */
    private NopDatavDashboardSnapshot readLatestSnapshot(String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        NopDatavDashboardSnapshot snapshot = daoProvider()
                .daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_NOT_FOUND).param(ARG_DASHBOARD_ID, dashboardId);
        }
        return snapshot;
    }
}
