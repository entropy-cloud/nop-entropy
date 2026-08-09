package io.nop.datav.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavDashboard;

import java.util.Set;

import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_USER_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_NOT_DASHBOARD_OWNER;

/**
 * 看板 owner/admin 校验 helper（D3-2 分享管理 action 复用）。
 *
 * <p>由于 {@code NopDatavDashboardShare} 实体本身无行级规则（避免匿名公共访问 fail-closed），
 * 分享管理 action 需显式校验当前用户为对应 Dashboard 的 owner 或 admin 角色。</p>
 */
public final class NopDatavDashboardOwnerGuard {

    public static final String ROLE_ADMIN = "admin";

    private NopDatavDashboardOwnerGuard() {
    }

    /**
     * 加载 Dashboard 并校验当前用户为 owner 或 admin；否则抛 {@link #ERR_DATAV_NOT_DASHBOARD_OWNER}。
     *
     * @param daoProvider  DAO 提供者
     * @param dashboardId  看板 ID
     * @param context      服务上下文（需含登录用户）
     * @return 已加载的 Dashboard 实体
     */
    public static NopDatavDashboard requireDashboardOwnership(IDaoProvider daoProvider, String dashboardId,
                                                               IServiceContext context) {
        NopDatavDashboard dashboard = daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_DASHBOARD_NOT_FOUND).param(ARG_DASHBOARD_ID, dashboardId);
        }
        String userName = NopDatavOperatorResolver.resolveOperator(context);
        if (!isOwnerOrAdmin(dashboard, userName, context)) {
            throw new NopException(ERR_DATAV_NOT_DASHBOARD_OWNER)
                    .param(ARG_USER_NAME, userName)
                    .param(ARG_DASHBOARD_ID, dashboardId);
        }
        return dashboard;
    }

    /**
     * 是否为 owner（createdBy 匹配）或 admin 角色。
     */
    private static boolean isOwnerOrAdmin(NopDatavDashboard dashboard, String userName, IServiceContext context) {
        if (dashboard.getCreatedBy() != null && dashboard.getCreatedBy().equals(userName)) {
            return true;
        }
        IUserContext userContext = context == null ? null : context.getUserContext();
        if (userContext == null) {
            return false;
        }
        Set<String> roles = userContext.getRoles();
        return roles != null && roles.contains(ROLE_ADMIN);
    }
}
