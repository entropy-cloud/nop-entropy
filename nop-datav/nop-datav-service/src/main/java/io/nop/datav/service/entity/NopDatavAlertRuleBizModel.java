package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.datav.biz.INopDatavAlertRuleBiz;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavAlertState;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.alert.AlertEvaluator;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;

import jakarta.inject.Inject;
import java.sql.Timestamp;

import static io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_USER_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_NOT_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_RULE_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;

/**
 * 告警规则 BizModel（D5-2，schedule-report-design.md §21/§23）。
 *
 * <p>CRUD 继承 {@link CrudBizModel}（自动提供 query/mutation）+ 自定义 action：
 * {@link #enableAlertRule}/{@link #disableAlertRule}/{@link #evaluateAlertNow}/{@link #getAlertState}。
 * action 经 {@code @Auth} + 看板 owner 校验（规则经 panelId → panel.dashboardId → dashboard 间接归属）。</p>
 *
 * <p><b>调度注册联动</b>：save（status=ENABLED）/enableAlertRule 调 {@link NopDatavAlertScheduler#registerRule}；
 * disableAlertRule/delete 调 {@link NopDatavAlertScheduler#unregisterRule}。</p>
 *
 * <p><b>状态初始化</b>：规则创建时同步初始化 {@link NopDatavAlertState}（{@code state=OK}）。</p>
 *
 * <p><b>手动评估</b>：{@link #evaluateAlertNow} 同步调 {@link AlertEvaluator#evaluate}，
 * 便于测试断言即时结果。若评估失败（如面板缺失/查询失败），返回当前状态实体（errorMsg 已记）。</p>
 */
@BizModel("NopDatavAlertRule")
public class NopDatavAlertRuleBizModel extends CrudBizModel<NopDatavAlertRule>
        implements INopDatavAlertRuleBiz {

    @Inject
    protected NopDatavAlertScheduler alertScheduler;

    @Inject
    protected AlertEvaluator alertEvaluator;

    public NopDatavAlertRuleBizModel() {
        setEntityName(NopDatavAlertRule.class.getName());
    }

    // ==================== save / delete override（联动调度注册 + 状态初始化） ====================

    @Override
    protected void afterEntityChange(NopDatavAlertRule entity, String action, IServiceContext context) {
        if (entity == null) {
            return;
        }
        // 创建规则时初始化 NopDatavAlertState(state=OK)（schedule-report-design.md §18）
        if (io.nop.biz.BizConstants.METHOD_SAVE.equals(action)) {
            initializeStateIfAbsent(entity);
        }
        // 联动调度注册
        if (alertScheduler != null) {
            if (NopDatavReportTaskStatus.isEnabled(entity.getStatus() == null ? 0 : entity.getStatus())) {
                alertScheduler.registerRule(entity.getAlertRuleId());
            } else {
                alertScheduler.unregisterRule(entity.getAlertRuleId());
            }
        }
    }

    private void initializeStateIfAbsent(NopDatavAlertRule rule) {
        IEntityDao<NopDatavAlertState> dao = daoProvider().daoFor(NopDatavAlertState.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopDatavAlertState.PROP_NAME_alertRuleId, rule.getAlertRuleId()));
        NopDatavAlertState existing = dao.findFirstByQuery(q);
        if (existing != null) {
            return;
        }
        alertEvaluator.createInitialState(rule);
    }

    // ==================== 自定义 action ====================

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavAlertRule:enableAlertRule")
    public NopDatavAlertRule enableAlertRule(@Name("id") String alertRuleId, IServiceContext context) {
        NopDatavAlertRule rule = requireRuleWithOwnership(alertRuleId, context);
        rule.setStatus(NopDatavReportTaskStatus.ENABLED);
        touchUpdate(rule, NopDatavOperatorResolver.resolveOperator(context));
        daoProvider().daoFor(NopDatavAlertRule.class).updateEntityDirectly(rule);
        if (alertScheduler != null) {
            alertScheduler.registerRule(alertRuleId);
        }
        return rule;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavAlertRule:disableAlertRule")
    public NopDatavAlertRule disableAlertRule(@Name("id") String alertRuleId, IServiceContext context) {
        NopDatavAlertRule rule = requireRuleWithOwnership(alertRuleId, context);
        if (alertScheduler != null) {
            alertScheduler.unregisterRule(alertRuleId);
        }
        rule.setStatus(NopDatavReportTaskStatus.DISABLED);
        touchUpdate(rule, NopDatavOperatorResolver.resolveOperator(context));
        daoProvider().daoFor(NopDatavAlertRule.class).updateEntityDirectly(rule);
        return rule;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavAlertRule:evaluateAlertNow")
    public NopDatavAlertState evaluateAlertNow(@Name("id") String alertRuleId, IServiceContext context) {
        NopDatavAlertRule rule = requireRuleWithOwnership(alertRuleId, context);
        // 同步评估一次（业务异常如未配置发件人/无渠道会抛错——这是显式失败，非静默跳过）
        alertEvaluator.evaluate(alertRuleId);
        return getAlertState(alertRuleId, context);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavAlertRule:getAlertState")
    public NopDatavAlertState getAlertState(@Name("id") String alertRuleId, IServiceContext context) {
        // owner 校验（经 panelId → dashboard 间接归属）
        requireRuleWithOwnership(alertRuleId, context);
        IEntityDao<NopDatavAlertState> dao = daoProvider().daoFor(NopDatavAlertState.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopDatavAlertState.PROP_NAME_alertRuleId, alertRuleId));
        return dao.findFirstByQuery(q);
    }

    // ==================== 校验 helpers ====================

    /**
     * 加载规则 + 看板 owner 校验：经 rule.panelId 加载 panel → panel.dashboardId 加载看板，
     * 校验当前用户为 owner 或 admin。失败抛 {@link NopDatavErrors#ERR_DATAV_ALERT_NOT_OWNER}。
     */
    private NopDatavAlertRule requireRuleWithOwnership(String alertRuleId, IServiceContext context) {
        IEntityDao<NopDatavAlertRule> dao = daoProvider().daoFor(NopDatavAlertRule.class);
        NopDatavAlertRule rule = dao.getEntityById(alertRuleId);
        if (rule == null) {
            throw new NopException(ERR_DATAV_ALERT_RULE_NOT_FOUND).param(ARG_ALERT_RULE_ID, alertRuleId);
        }

        // 经 panelId → panel → dashboardId 间接归属校验
        IEntityDao<NopDatavPanel> panelDao = daoProvider().daoFor(NopDatavPanel.class);
        NopDatavPanel panel = panelDao.getEntityById(rule.getPanelId());
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND)
                    .param(ARG_PANEL_ID, rule.getPanelId())
                    .param(ARG_ALERT_RULE_ID, alertRuleId);
        }
        String dashboardId = panel.getDashboardId();
        IEntityDao<NopDatavDashboard> dashDao = daoProvider().daoFor(NopDatavDashboard.class);
        NopDatavDashboard dashboard = dashDao.getEntityById(dashboardId);
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_DASHBOARD_NOT_FOUND).param(ARG_DASHBOARD_ID, dashboardId);
        }

        String userName = NopDatavOperatorResolver.resolveOperator(context);
        if (!isOwnerOrAdmin(dashboard, userName, context)) {
            throw new NopException(ERR_DATAV_ALERT_NOT_OWNER)
                    .param(ARG_USER_NAME, userName)
                    .param(ARG_ALERT_RULE_ID, alertRuleId);
        }
        return rule;
    }

    private static boolean isOwnerOrAdmin(NopDatavDashboard dashboard, String userName, IServiceContext context) {
        if (dashboard.getCreatedBy() != null && dashboard.getCreatedBy().equals(userName)) {
            return true;
        }
        io.nop.api.core.auth.IUserContext userContext = context == null ? null : context.getUserContext();
        if (userContext == null) {
            return false;
        }
        java.util.Set<String> roles = userContext.getRoles();
        return roles != null && roles.contains("admin");
    }

    private static void touchUpdate(NopDatavAlertRule rule, String operator) {
        rule.setUpdatedBy(operator);
        rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * 测试辅助：暴露调度器（断言 registerRule/unregisterRule 被调用）。
     */
    public NopDatavAlertScheduler getAlertScheduler() {
        return alertScheduler;
    }

    /**
     * 测试辅助：暴露评估器（断言 evaluate 被调用）。
     */
    public AlertEvaluator getAlertEvaluator() {
        return alertEvaluator;
    }
}
