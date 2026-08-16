package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;

import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.JumpResult;
import io.nop.datav.biz.LinkageResult;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.linkage.LinkageExecutor;
import io.nop.datav.service.query.PanelDataBinder;
import io.nop.datav.service.report.NopDatavReportTaskStatus;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;

/**
 * 面板 BizModel。
 *
 * <p><b>删除生命周期（plan 2026-08-14-2020-1，D3/D5 裁定）</b>：标准 {@code delete(id)} 路径
 * （含 batchDelete/deleteByQuery 收敛的 {@code doDeleteEntity}）在主表行删除后，把按 {@code panelId}
 * 关联的 AlertRule 置 {@code status=DISABLED}（先落库）并即时 {@code unregisterRule}（事务边界见
 * schedule-report-design.md §26）。任一子步骤失败显式抛错。</p>
 */
@BizModel("NopDatavPanel")
public class NopDatavPanelBizModel extends CrudBizModel<NopDatavPanel> implements INopDatavPanelBiz {

    @jakarta.inject.Inject
    protected IJdbcTemplate jdbcTemplate;

    @jakarta.inject.Inject
    protected NopDatavAlertScheduler alertScheduler;

    public NopDatavPanelBizModel() {
        setEntityName(NopDatavPanel.class.getName());
    }

    // ==================== 删除生命周期联动（plan 2026-08-14-2020-1） ====================

    /**
     * 标准删除路径级联挂接点（D5 裁定）：{@code delete(id)} / {@code batchDelete} / {@code deleteByQuery}
     * 均虚分派到本方法；级联在 {@code super} 之后执行（权限校验通过后才产生调度注销副作用）。
     */
    @Override
    protected void doDeleteEntity(@Name("entity") NopDatavPanel entity,
                                  @Name("refNamesToCheck") Set<String> refNamesToCheck,
                                  @Name("prepareDelete") BiConsumer<NopDatavPanel, IServiceContext> prepareDelete,
                                  IServiceContext context) {
        super.doDeleteEntity(entity, refNamesToCheck, prepareDelete, context);
        disableAlertRulesForPanel(entity.getPanelId(), context);
    }

    /**
     * 按 panelId 停用关联 AlertRule（status=DISABLED 先落库）并即时 unregisterRule（D3 双动作）。
     */
    private void disableAlertRulesForPanel(String panelId, IServiceContext context) {
        IEntityDao<NopDatavAlertRule> dao = daoProvider().daoFor(NopDatavAlertRule.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("panelId", panelId));
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        for (NopDatavAlertRule rule : dao.findAllByQuery(query)) {
            if (rule.getStatus() == null || rule.getStatus() != NopDatavReportTaskStatus.DISABLED) {
                rule.setStatus(NopDatavReportTaskStatus.DISABLED);
                rule.setUpdatedBy(operator);
                rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                dao.updateEntityDirectly(rule);
            }
            if (alertScheduler != null) {
                alertScheduler.unregisterRule(rule.getAlertRuleId());
            }
        }
    }

    /**
     * 面板归属权限（P0-02 修复，plan 2026-08-15-2146-1）：面板数据类自定义 action 的权限锚点是
     * 所属 Dashboard 的行级规则（Panel 实体无 RLS，{@code requireEntity(Panel)} 的 checkDataAuth 恒放行）。
     * 镜像 {@code NopDatavExportTaskBizModel.requireSourceAccess} 先例：经
     * {@code checker.isPermitted("NopDatavDashboard", action, dashboard, context)} 校验
     * （user 规则 = createdBy 匹配 OR publishStatus=10），不通过抛 {@code ERR_AUTH_NO_DATA_AUTH}。
     */
    private static final String DASHBOARD_AUTH_OBJ = "NopDatavDashboard";

    private void requirePanelDashboardAccess(NopDatavPanel panel, String action, IServiceContext context) {
        NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class)
                .getEntityById(panel.getDashboardId());
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_DASHBOARD_NOT_FOUND).param(ARG_DASHBOARD_ID, panel.getDashboardId());
        }
        IDataAuthChecker checker = context == null ? null : context.getDataAuthChecker();
        if (checker == null) {
            return;
        }
        if (!checker.isPermitted(DASHBOARD_AUTH_OBJ, action, dashboard, context)) {
            throw new NopException(ERR_AUTH_NO_DATA_AUTH).param(ARG_BIZ_OBJ_NAME, DASHBOARD_AUTH_OBJ);
        }
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavPanel:getPanelData")
    public PanelDataResult getPanelData(@Name("id") String id,
                                        @Name("params") Map<String, Object> requestParams,
                                        IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "getPanelData", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        requirePanelDashboardAccess(panel, "getPanelData", context);
        IDaoProvider daoProvider = daoProvider();
        return new PanelDataBinder(daoProvider, jdbcTemplate).queryPanelData(id, panel, requestParams);
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavPanel:refreshPanel")
    public PanelDataResult refreshPanel(@Name("id") String id, IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "refreshPanel", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        requirePanelDashboardAccess(panel, "refreshPanel", context);
        IDaoProvider daoProvider = daoProvider();
        // 复用 getPanelData 的数据绑定管线，触发重新查询并返回最新结果（不重复实现查询逻辑）
        return new PanelDataBinder(daoProvider, jdbcTemplate).queryPanelData(id, panel, null);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavPanel:resolveLinkage")
    public LinkageResult resolveLinkage(@Name("id") String id,
                                        @Name("clickContext") Map<String, Object> clickContext,
                                        IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "resolveLinkage", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        requirePanelDashboardAccess(panel, "resolveLinkage", context);
        return new LinkageExecutor(daoProvider()).resolveLinkage(id, panel, clickContext);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavPanel:resolveJump")
    public JumpResult resolveJump(@Name("id") String id,
                                  @Name("clickContext") Map<String, Object> clickContext,
                                  IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "resolveJump", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        requirePanelDashboardAccess(panel, "resolveJump", context);
        return new LinkageExecutor(daoProvider()).resolveJump(id, panel, clickContext);
    }
}
