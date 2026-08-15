package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;

import io.nop.datav.biz.DashboardDataResult;
import io.nop.datav.biz.DashboardPanelDataItem;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavFilterState;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.NopDatavConfigs;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.filter.DashboardFilterResolver;
import io.nop.datav.service.filter.DashboardFilterUrlCodec;
import io.nop.datav.service.filter.DashboardParamDefinition;
import io.nop.datav.service.filter.DashboardParamParser;
import io.nop.datav.service.layout.DashboardLayoutCodec;
import io.nop.datav.service.query.DashboardPanelQueryCache;
import io.nop.datav.service.query.PanelDataBinder;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_MAX_PANELS;
import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_COUNT;
import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_IN_DASHBOARD;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND;

/**
 * 看板 BizModel。
 *
 * <p><b>删除生命周期（plan 2026-08-14-2020-1，裁定见 permission-sharing-design.md「删除生命周期与分享吊销」）
 * </b>：标准 {@code delete(id)} 路径（含 batchDelete/deleteByQuery 收敛的 {@code doDeleteEntity}）在主表行
 * 删除后级联处理：关联 ReportTask/AlertRule 置 {@code DISABLED} 并即时注销 cron job、该看板全部分享置
 * {@code enabled=0}、子对象（Panel/Tab/DatasetRef/FilterState）与发布快照物理删除。任一子步骤失败显式抛错。</p>
 */
@BizModel("NopDatavDashboard")
public class NopDatavDashboardBizModel extends CrudBizModel<NopDatavDashboard>
        implements INopDatavDashboardBiz {

    public static final int PUBLISH_STATUS_DRAFT = 0;
    public static final int PUBLISH_STATUS_PUBLISHED = 10;

    /**
     * 分享启用标记（domain boolFlag）：1=启用，0=禁用（D2 逻辑吊销值）。
     */
    private static final byte SHARE_ENABLED_TRUE = 1;

    @jakarta.inject.Inject
    protected IJdbcTemplate jdbcTemplate;

    @jakarta.inject.Inject
    protected io.nop.orm.IOrmTemplate ormTemplate;

    @jakarta.inject.Inject
    protected NopDatavAlertScheduler alertScheduler;

    @jakarta.inject.Inject
    protected NopDatavReportScheduler reportScheduler;

    public NopDatavDashboardBizModel() {
        setEntityName(NopDatavDashboard.class.getName());
    }

    // ==================== 删除生命周期级联（plan 2026-08-14-2020-1） ====================

    /**
     * 标准删除路径级联挂接点（D5 裁定）：{@code delete(id)} / {@code batchDelete} / {@code deleteByQuery}
     * 均虚分派到本方法。级联在 {@code super} 之后执行——权限校验（checkMetaFilter/checkDataAuth）通过后
     * 才产生调度注销等非事务性副作用。任一子步骤失败异常传播（无静默跳过）。
     */
    @Override
    protected void doDeleteEntity(@Name("entity") NopDatavDashboard entity,
                                  @Name("refNamesToCheck") Set<String> refNamesToCheck,
                                  @Name("prepareDelete") BiConsumer<NopDatavDashboard, IServiceContext> prepareDelete,
                                  IServiceContext context) {
        super.doDeleteEntity(entity, refNamesToCheck, prepareDelete, context);
        handleDeleteCascade(entity, context);
    }

    private void handleDeleteCascade(NopDatavDashboard dashboard, IServiceContext context) {
        String dashboardId = dashboard.getDashboardId();
        IDaoProvider daoProvider = daoProvider();
        String operator = NopDatavOperatorResolver.resolveOperator(context);

        // AlertRule 经 panelId 定位，必须先于 Panel 行删除读取面板清单
        List<NopDatavPanel> panels = findRelatedEntities(NopDatavPanel.class, "dashboardId", dashboardId, null);

        disableReportTasksForDashboard(daoProvider, dashboardId, operator);
        disableAlertRulesForPanels(daoProvider, panels, operator);
        revokeSharesForDashboard(daoProvider, dashboardId, operator);

        deleteAllByDashboard(daoProvider, NopDatavFilterState.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavDatasetRef.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavDashboardTab.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavPanel.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavDashboardSnapshot.class, dashboardId);
    }

    /**
     * 关联 ReportTask（按 dashboardId）：置 status=DISABLED（先落库）并即时 unregisterTask（D3 双动作，
     * 事务边界见 schedule-report-design.md §26——注销失败异常传播回滚整个删除）。
     */
    private void disableReportTasksForDashboard(IDaoProvider daoProvider, String dashboardId, String operator) {
        IEntityDao<NopDatavReportTask> dao = daoProvider.daoFor(NopDatavReportTask.class);
        for (NopDatavReportTask task : findAllByField(dao, "dashboardId", dashboardId)) {
            if (task.getStatus() == null || task.getStatus() != NopDatavReportTaskStatus.DISABLED) {
                task.setStatus(NopDatavReportTaskStatus.DISABLED);
                task.setUpdatedBy(operator);
                task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                dao.updateEntityDirectly(task);
            }
            if (reportScheduler != null) {
                reportScheduler.unregisterTask(task.getReportTaskId());
            }
        }
    }

    /**
     * 关联 AlertRule（经 panel.dashboardId 定位）：置 status=DISABLED 并即时 unregisterRule。
     */
    private void disableAlertRulesForPanels(IDaoProvider daoProvider, List<NopDatavPanel> panels, String operator) {
        if (panels.isEmpty()) {
            return;
        }
        List<String> panelIds = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            panelIds.add(panel.getPanelId());
        }
        IEntityDao<NopDatavAlertRule> dao = daoProvider.daoFor(NopDatavAlertRule.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("panelId", panelIds));
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
     * 该看板全部分享逻辑吊销（D2：enabled=0，保留行可审计；已禁用行不动避免版本扰动）。
     */
    private void revokeSharesForDashboard(IDaoProvider daoProvider, String dashboardId, String operator) {
        IEntityDao<NopDatavDashboardShare> dao = daoProvider.daoFor(NopDatavDashboardShare.class);
        for (NopDatavDashboardShare share : findAllByField(dao, "dashboardId", dashboardId)) {
            if (share.getEnabled() != null && share.getEnabled() == SHARE_ENABLED_TRUE) {
                share.setEnabled((byte) 0);
                share.setUpdatedBy(operator);
                share.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                dao.updateEntityDirectly(share);
            }
        }
    }

    /**
     * 按 dashboardId 物理删除子表全部行（D1：Panel/Tab/DatasetRef/FilterState/Snapshot 级联）。
     */
    private void deleteAllByDashboard(IDaoProvider daoProvider, Class<? extends IDaoEntity> entityClass,
                                      String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        daoProvider.daoFor(entityClass).deleteByQuery(query);
    }

    private static <T extends IDaoEntity> List<T> findAllByField(IEntityDao<T> dao, String field, String value) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(field, value));
        return dao.findAllByQuery(query);
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboard:publishDashboard")
    public NopDatavDashboardSnapshot publishDashboard(@Name("id") String id, IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "publishDashboard", context);

        String snapshotContent = serializeDashboardContent(dashboard);
        long nextVersion = calculateNextVersion(id);
        String publishedBy = NopDatavOperatorResolver.resolveOperator(context);
        Timestamp publishedTime = new Timestamp(System.currentTimeMillis());

        NopDatavDashboardSnapshot snapshot = daoProvider()
                .daoFor(NopDatavDashboardSnapshot.class).newEntity();
        snapshot.setSnapshotId(generateSnapshotId());
        snapshot.setDashboardId(dashboard.getDashboardId());
        snapshot.setSnapshotVersion(nextVersion);
        snapshot.setSnapshotContent(snapshotContent);
        snapshot.setPublishedBy(publishedBy);
        snapshot.setPublishedTime(publishedTime);
        snapshot.setVersion(0L);
        snapshot.setCreatedBy(publishedBy);
        snapshot.setCreateTime(publishedTime);
        snapshot.setUpdatedBy(publishedBy);
        snapshot.setUpdateTime(publishedTime);

        daoProvider().daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(snapshot);

        updateDashboardPublishState(dashboard.getDashboardId(), PUBLISH_STATUS_PUBLISHED,
                nextVersion, publishedBy, publishedTime);

        afterEntityChange(dashboard, "publishDashboard", context);
        return snapshot;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:getPublishedDashboard")
    public NopDatavDashboardSnapshot getPublishedDashboard(@Name("id") String id, IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "getPublishedDashboard", context);

        NopDatavDashboardSnapshot snapshot = findLatestSnapshot(dashboard.getDashboardId());
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_NOT_FOUND)
                    .param("dashboardId", dashboard.getDashboardId());
        }
        return snapshot;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboard:rollbackDashboard")
    public NopDatavDashboardSnapshot rollbackDashboard(@Name("id") String id,
                                                       @Name("snapshotVersion") long snapshotVersion,
                                                       IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "rollbackDashboard", context);

        NopDatavDashboardSnapshot snapshot = findSnapshotByVersion(dashboard.getDashboardId(), snapshotVersion);
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND)
                    .param("dashboardId", dashboard.getDashboardId())
                    .param("snapshotVersion", snapshotVersion);
        }

        restoreDashboardFromSnapshot(dashboard, snapshot);
        updateDashboardFields(dashboard.getDashboardId(), dashboard);

        afterEntityChange(dashboard, "rollbackDashboard", context);
        return snapshot;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:resolveFilterValues")
    public Map<String, Object> resolveFilterValues(@Name("id") String id,
                                                    @Name("filterValues") Map<String, Object> filterValues,
                                                    IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "resolveFilterValues", context);
        List<DashboardParamDefinition> definitions = DashboardParamParser.parse(dashboard.getParamConfig());
        return DashboardFilterResolver.resolve(definitions, filterValues);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:parseFilterFromUrl")
    public Map<String, Object> parseFilterFromUrl(@Name("id") String id,
                                                   @Name("url") String url,
                                                   IServiceContext context) {
        requireEntity(id, "parseFilterFromUrl", context);
        Map<String, Object> parsed = DashboardFilterUrlCodec.parseQueryString(url);
        if (parsed.isEmpty()) {
            return Collections.emptyMap();
        }
        NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class)
                .getEntityById(id);
        List<DashboardParamDefinition> definitions = DashboardParamParser.parse(dashboard.getParamConfig());
        return DashboardFilterResolver.resolve(definitions, parsed);
    }

    // ==================== 批量面板查询（plan 2026-08-14-2020-2，裁定见 runtime-design.md §四；并行执行与结果缓存 plan 2026-08-15-0004-3，裁定见 §八） ====================

    /**
     * 面板查询单元任务：执行一个面板的查询并返回结果条目（NopException 已在任务内归集为面板级失败条目）。
     */
    @FunctionalInterface
    public interface PanelQueryTask {
        DashboardPanelDataItem call() throws Exception;
    }

    /**
     * 测试可观测 seam（P1 裁定，runtime-design.md §8.1）：面板任务装饰器，生产恒 null。
     * 测试注入 latch/并发计数器等，对「并发执行证明」「并行度上界不被突破」做确定性断言。
     */
    @FunctionalInterface
    public interface PanelQueryTaskDecorator {
        PanelQueryTask decorate(NopDatavPanel panel, PanelQueryTask task);
    }

    /**
     * 测试可观测 seam（P1 裁定）：面板任务装饰器。仅测试注入；生产路径恒 null（零开销直通）。
     */
    private volatile PanelQueryTaskDecorator panelQueryTaskDecorator;

    /**
     * 测试可观测 seam（P1 裁定）：面板查询执行器覆盖。默认 null → {@link #getPanelQueryExecutor()}
     * 返回共享 {@code GlobalExecutors.globalWorker()}（跨请求复用，无每请求新建/关闭）。
     */
    private volatile java.util.concurrent.Executor panelQueryExecutorOverride;

    /** 测试 seam：注入面板任务装饰器（确定性断言用）。 */
    public void setPanelQueryTaskDecorator(PanelQueryTaskDecorator decorator) {
        this.panelQueryTaskDecorator = decorator;
    }

    /** 测试可观测 seam：面板查询执行器（默认共享 globalWorker；identity 断言证明跨请求复用）。 */
    public java.util.concurrent.Executor getPanelQueryExecutor() {
        java.util.concurrent.Executor override = panelQueryExecutorOverride;
        return override != null ? override : GlobalExecutors.globalWorker();
    }

    /** 测试 seam：覆盖面板查询执行器（仅测试注入）。 */
    public void setPanelQueryExecutor(java.util.concurrent.Executor executor) {
        this.panelQueryExecutorOverride = executor;
    }

    /**
     * 批量查询看板面板数据（D1 归属：看板视角）。
     *
     * <p>行为（裁定见 {@code ai-dev/design/nop-datav/runtime-design.md} §4.7）：requireEntity 校验看板存活
     * （D5：live 表 + 发布非前置）→ 看板级筛选一次求值（§4.6：与逐面板 resolveFilterValues + getPanelData
     * 组合语义等价）→ 按 sortOrder 加载面板（D2：可选 panelIds 子集，越权引用整体显式报错，条目顺序跟随
     * sortOrder，重复 id 去重）→ 上限校验（D4：先于任何面板查询执行）→ 面板查询（§8.1：默认有界并行，
     * 开关关闭或面板数 ≤1 走顺序路径）复用 {@link PanelDataBinder} → 聚合响应（D3：面板纳入集含无数据集
     * 面板；仅 NopException 捕获为面板级失败，其余按看板级失败传播——两种执行模式下逐条等价）。</p>
     */
    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:getDashboardData")
    public DashboardDataResult getDashboardData(@Name("id") String id,
                                                @Name("params") Map<String, Object> params,
                                                @Name("panelIds") List<String> panelIds,
                                                IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "getDashboardData", context);

        // 看板级筛选一次求值，统一应用到全部面板（resolver 是纯函数，一次求值与逐面板求值结果一致）
        List<DashboardParamDefinition> definitions = DashboardParamParser.parse(dashboard.getParamConfig());
        Map<String, Object> resolvedParams = DashboardFilterResolver.resolve(definitions, params);

        // 面板纳入集：按 sortOrder 加载看板全部面板（对齐 exportDashboard 加载先例，但纳入语义按 D3 不排除无数据集面板）
        List<NopDatavPanel> panels = findRelatedEntities(NopDatavPanel.class, "dashboardId", id, "sortOrder");
        if (panelIds != null && !panelIds.isEmpty()) {
            // 重复 id 去重（LinkedHashSet 保持首次出现顺序，仅用于越权报告的确定性）
            Set<String> requestedIds = new LinkedHashSet<>(panelIds);
            Set<String> unknownIds = new LinkedHashSet<>(requestedIds);
            for (NopDatavPanel panel : panels) {
                unknownIds.remove(panel.getPanelId());
            }
            if (!unknownIds.isEmpty()) {
                // D2：不存在/不属于该看板的 id 整体显式报错，禁止静默忽略不标注
                throw new NopException(ERR_DATAV_PANEL_NOT_IN_DASHBOARD)
                        .param(ARG_DASHBOARD_ID, id)
                        .param(ARG_PANEL_ID, unknownIds.iterator().next());
            }
            // 条目顺序跟随面板 sortOrder（不跟随 panelIds 传入顺序）
            List<NopDatavPanel> subset = new ArrayList<>(panels.size());
            for (NopDatavPanel panel : panels) {
                if (requestedIds.contains(panel.getPanelId())) {
                    subset.add(panel);
                }
            }
            panels = subset;
        }

        // D4：上限校验先于任何面板查询执行（防单请求放大为海量 SQL；并行模式下同样先于任何任务提交）
        int maxPanels = NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get();
        if (panels.size() > maxPanels) {
            throw new NopException(ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED)
                    .param(ARG_PANEL_COUNT, panels.size())
                    .param(ARG_MAX_PANELS, maxPanels);
        }

        // §8.1：默认有界并行；开关关闭或单面板走原顺序路径（与并行前行为逐条等价）
        boolean parallel = NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED.get() && panels.size() > 1;
        List<DashboardPanelDataItem> items = parallel
                ? executePanelQueriesInParallel(panels, resolvedParams, context)
                : executePanelQueriesSequentially(panels, resolvedParams);
        return new DashboardDataResult(id, items);
    }

    /**
     * 查询结果缓存实例（§8.3：进程内 LocalCache，单节点语义）。惰性构建（首次启用时），容量/TTL/准入
     * 上界在构建时读配置——配置变更在缓存实例重建后生效（进程重启或测试重置钩子）；启用开关按请求判定。
     */
    private volatile DashboardPanelQueryCache queryResultCache;

    /** 获取（惰性构建）查询结果缓存实例。 */
    private DashboardPanelQueryCache getQueryResultCache() {
        DashboardPanelQueryCache cache = queryResultCache;
        if (cache == null) {
            synchronized (this) {
                if (queryResultCache == null) {
                    queryResultCache = DashboardPanelQueryCache.fromConfigs();
                }
                cache = queryResultCache;
            }
        }
        return cache;
    }

    /** 测试可观测 seam：当前缓存实例（未构建时以当前配置构建；命中/未命中计数断言用）。 */
    public DashboardPanelQueryCache getQueryResultCacheForTest() {
        return getQueryResultCache();
    }

    /** 测试 hygiene：重建缓存实例（清空条目并以当前配置重建，TTL/容量配置变更后生效）。 */
    public void resetQueryResultCacheForTest() {
        synchronized (this) {
            queryResultCache = null;
        }
    }

    /**
     * 顺序执行面板查询（并行前原路径，行为逐条保持）。
     *
     * <p>D3：仅 NopException 捕获为面板级失败（PanelDataBinder 全部错误路径均抛 NopException），
     * 非 NopException 的意外 RuntimeException 视为系统性故障按看板级失败传播（不吞掉）。</p>
     */
    private List<DashboardPanelDataItem> executePanelQueriesSequentially(List<NopDatavPanel> panels,
                                                                         Map<String, Object> resolvedParams) {
        PanelDataBinder binder = newPanelDataBinder();
        DashboardPanelQueryCache cache = resolveQueryResultCache();
        List<DashboardPanelDataItem> items = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            PanelQueryTask task = newPanelQueryTask(panel, binder, resolvedParams, cache);
            PanelQueryTaskDecorator decorator = panelQueryTaskDecorator;
            if (decorator != null) {
                task = decorator.decorate(panel, task);
            }
            items.add(runPanelQueryTask(task, panel));
        }
        return items;
    }

    /**
     * 有界并行执行面板查询（§8.1 裁定：共享 globalWorker + 请求内 Semaphore 并行度）。
     *
     * <p>错误归集（D3 在并发下保持）：NopException 在任务内归集为面板级失败条目；非 NopException 的任务
     * 异常在 join 全部任务后按面板顺序重抛第一个（看板级失败传播，确定性）；执行器拒绝/中断显式传播，
     * 无任务静默丢弃。结果按面板下标归位，条目顺序恒等于顺序版（sortOrder）。worker 线程经
     * {@code IContext.executeWithContext} 绑定调用方上下文（§8.2：tenant/locale/callExpireTime 语义一致）。</p>
     */
    private List<DashboardPanelDataItem> executePanelQueriesInParallel(List<NopDatavPanel> panels,
                                                                       Map<String, Object> resolvedParams,
                                                                       IServiceContext context) {
        PanelDataBinder binder = newPanelDataBinder();
        DashboardPanelQueryCache cache = resolveQueryResultCache();
        // §8.2：捕获调用方 IContext，worker 任务绑定执行（dao 层消费 currentTenantId/callExpireTime）
        IContext callerContext = context != null && context.getContext() != null
                ? context.getContext() : ContextProvider.currentContext();
        Semaphore permits = new Semaphore(Math.max(1, NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLELISM.get()));
        java.util.concurrent.Executor executor = getPanelQueryExecutor();

        List<CompletableFuture<DashboardPanelDataItem>> futures = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            PanelQueryTask task = newPanelQueryTask(panel, binder, resolvedParams, cache);
            PanelQueryTaskDecorator decorator = panelQueryTaskDecorator;
            if (decorator != null) {
                task = decorator.decorate(panel, task);
            }
            PanelQueryTask decorated = task;
            Semaphore semaphore = permits;
            futures.add(CompletableFuture.supplyAsync(
                    () -> runPanelQueryTaskBounded(decorated, callerContext, semaphore), executor));
        }
        // join 全部任务（无静默丢弃）；失败任务按面板顺序取第一个重抛
        RuntimeException dashboardFailure = null;
        List<DashboardPanelDataItem> items = new ArrayList<>(futures.size());
        for (CompletableFuture<DashboardPanelDataItem> future : futures) {
            DashboardPanelDataItem item = null;
            try {
                item = future.join();
            } catch (CompletionException ce) {
                Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                if (cause instanceof Error) {
                    // Error（OOM 等）立即传播，不与业务失败归集排序
                    throw (Error) cause;
                }
                if (dashboardFailure == null) {
                    dashboardFailure = cause instanceof RuntimeException
                            ? (RuntimeException) cause : new IllegalStateException("panel query task failed", cause);
                }
            }
            items.add(item);
        }
        if (dashboardFailure != null) {
            // D3 并行保持：非 NopException 按看板级失败传播（首个，按面板顺序，确定性）
            throw dashboardFailure;
        }
        return items;
    }

    /** 面板工厂 seam（P3 缓存接入点预留）：批量路径的 PanelDataBinder 构造。 */
    protected PanelDataBinder newPanelDataBinder() {
        return new PanelDataBinder(daoProvider(), jdbcTemplate);
    }

    /** 按请求判定缓存接入（§8.5：开关默认关；关闭时返回 null → binder 缓存链路零触达，与缓存前等价）。 */
    private DashboardPanelQueryCache resolveQueryResultCache() {
        return NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get() ? getQueryResultCache() : null;
    }

    /**
     * 单面板查询单元任务：执行查询并归集条目（NopException → 面板级失败条目；其余异常原样传播）。
     * 缓存透传 binder（仅批量路径接入，§8.3；null = 不缓存）。
     */
    private PanelQueryTask newPanelQueryTask(NopDatavPanel panel, PanelDataBinder binder,
                                             Map<String, Object> resolvedParams,
                                             DashboardPanelQueryCache cache) {
        return () -> {
            try {
                PanelDataResult result = binder.queryPanelData(panel.getPanelId(), panel, resolvedParams, null, cache);
                return DashboardPanelDataItem.success(result);
            } catch (NopException e) {
                return DashboardPanelDataItem.failure(panel.getPanelId(), e.getErrorCode(), safeMsg(e));
            }
        };
    }

    /** 同步执行单个面板任务（顺序路径复用；异常语义与并行路径一致）。 */
    private DashboardPanelDataItem runPanelQueryTask(PanelQueryTask task, NopDatavPanel panel) {
        try {
            return task.call();
        } catch (NopException e) {
            // 任务内已归集 NopException 为失败条目；此处 NopException 仅可能来自任务包装层，
            // 按看板级失败语义原样传播（与并行前顺序路径的隐式传播一致）
            throw e;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException("panel query task failed for panel " + panel.getPanelId(), e);
        }
    }

    /**
     * 并行路径的 worker 执行体：Semaphore 许可 + 任务专属上下文 + 新 ORM session。
     *
     * <p>上下文形态（§8.2 修订）：**每任务新建 context 并从调用方 context 拷贝 tenant/locale 等属性**
     * （{@code ContextProvider.propagateContext}），经 {@code executeWithContext} 绑定——禁止多 worker
     * 共享调用方同一 context 对象：平台 {@code TransactionRegistry} 挂在 context 上
     * （{@code TransactionRegistry.instance()} 经 {@code getOrCreateContext()} 定位），并发共享同一
     * context 会导致事务注册表交错损坏（平台对同 context 并发执行有显式 WARN）。</p>
     *
     * <p>新 ORM session：平台 worker 线程 DB 访问先例（{@code NopDatavExportTaskBizModel.submitExecution} /
     * {@code ReportDeliveryExecutor.execute} 均以 {@code ormTemplate.runInNewSession} 包裹 globalWorker
     * 任务）——session 与其事务注册随任务开闭，任务内全部 dao/jdbcTemplate 调用复用同一 session。</p>
     */
    private DashboardPanelDataItem runPanelQueryTaskBounded(PanelQueryTask task, IContext callerContext,
                                                            Semaphore permits) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("panel query task interrupted while awaiting parallelism permit", e);
        }
        try {
            IContext taskContext = newTaskContext(callerContext);
            if (taskContext == null) {
                return executePanelQueryTaskInNewSession(task);
            }
            return taskContext.executeWithContext(() -> executePanelQueryTaskInNewSession(task));
        } catch (RuntimeException e) {
            // NopException（含任务未归集的面板级错误）与框架层异常原样传播，由聚合层分级处理
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("panel query task failed", e);
        } finally {
            permits.release();
        }
    }

    /** 每任务新建 context（不共享调用方对象，防事务注册表并发损坏）并拷贝 tenant/locale 等属性。 */
    private static IContext newTaskContext(IContext callerContext) {
        if (callerContext == null) {
            return null;
        }
        IContext taskContext = ContextProvider.newContext(false);
        ContextProvider.propagateContext(taskContext, callerContext, false);
        return taskContext;
    }

    private DashboardPanelDataItem executePanelQueryTaskInNewSession(PanelQueryTask task) {
        return ormTemplate.runInNewSession(session -> {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("panel query task failed", e);
            }
        });
    }

    /**
     * 面板级失败条目的错误消息（null 安全，镜像 NopDatavExportTaskBizModel.safeMsg 先例；
     * 完整堆栈不适用于响应条目，errorCode+message 供调用方显式呈现）。
     */
    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    // ==================== flux 布局对齐（plan 2026-08-15-1134-1，裁定见 runtime-design.md §九） ====================

    /**
     * 导出看板布局 JSON（flux DashboardLayoutSchema 形态，编辑态 live 数据）。
     *
     * <p>契约（runtime-design.md §9）：几何读 layoutConfig（无几何面板默认布局合成 §9.5）；
     * 类型经 {@link PanelTypeMapping} 映射（未知显式报错 §9.3）；props 去 dataBinding 保护区（§9.6）；
     * 绑定面板 source=datasetRef:&lt;id&gt;（§9.9）。</p>
     */
    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:exportDashboardLayout")
    public Map<String, Object> exportDashboardLayout(@Name("id") String id, IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "exportDashboardLayout", context);
        List<NopDatavPanel> panels = findRelatedEntities(NopDatavPanel.class, "dashboardId", id, "sortOrder");
        return DashboardLayoutCodec.buildExportLayout(dashboard.getLayoutConfig(), panels);
    }

    /**
     * 保存 flux 编辑器产出的布局 JSON，reconcile 回归一化面板行（§9）。
     *
     * <p>阶段 A（纯校验/纯计算，先于任何写入——校验失败零落库）：载荷结构校验 → 上界校验 →
     * 身份对齐（既有/新建/重复/跨看板 id）→ layoutConfig 重建 + panelConfig 合并 + panelName 派生。
     * 阶段 B（写入）：删除（含 AlertRule 置 DISABLED + 注销 cron，镜像删除级联先例）→ 更新/新建 →
     * 主表 layoutConfig 更新。响应为再导出的布局 JSON（新建面板携带服务端 id，编辑器重同步）。</p>
     */
    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboard:saveDashboardLayout")
    public Map<String, Object> saveDashboardLayout(@Name("id") String id,
                                                   @Name("layout") Map<String, Object> layout,
                                                   IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "saveDashboardLayout", context);

        // ===== 阶段 A：纯校验与计算（无任何写入） =====
        DashboardLayoutCodec.SaveLayoutSpec spec = DashboardLayoutCodec.parseSavePayload(layout);

        int maxPanels = NopDatavConfigs.CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS.get();
        if (spec.getPanels().size() > maxPanels) {
            throw new NopException(ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED)
                    .param(ARG_PANEL_COUNT, spec.getPanels().size())
                    .param(ARG_MAX_PANELS, maxPanels);
        }

        List<NopDatavPanel> existingPanels = findRelatedEntities(NopDatavPanel.class, "dashboardId", id, "sortOrder");
        Map<String, NopDatavPanel> existingById = new LinkedHashMap<>();
        for (NopDatavPanel panel : existingPanels) {
            existingById.put(panel.getPanelId(), panel);
        }

        // 身份对齐：命中既有 panelId → 更新；未命中 → 服务端新建（跨看板 id 显式报错）
        List<String> unknownIds = new ArrayList<>();
        for (DashboardLayoutCodec.PanelSpec panelSpec : spec.getPanels()) {
            if (!existingById.containsKey(panelSpec.getId())) {
                unknownIds.add(panelSpec.getId());
            }
        }
        checkForeignPanelIds(unknownIds, id);

        // 最终 panelId（既有 id 或服务端生成）与每面板持久化预计算（容量校验在写入前完成）
        List<String> finalPanelIds = new ArrayList<>(spec.getPanels().size());
        List<String> mergedPanelConfigs = new ArrayList<>(spec.getPanels().size());
        List<String> panelNames = new ArrayList<>(spec.getPanels().size());
        for (int i = 0; i < spec.getPanels().size(); i++) {
            DashboardLayoutCodec.PanelSpec panelSpec = spec.getPanels().get(i);
            NopDatavPanel existing = existingById.get(panelSpec.getId());
            String finalId = existing != null ? existing.getPanelId() : generateLayoutPanelId();
            String existingConfig = existing != null ? existing.getPanelConfig() : null;
            finalPanelIds.add(finalId);
            mergedPanelConfigs.add(DashboardLayoutCodec.mergePanelConfig(
                    existingConfig, panelSpec.getProps(), finalId));
            panelNames.add(existing != null ? existing.getPanelName()
                    : DashboardLayoutCodec.derivePanelName(panelSpec.getTitle(), i));
        }
        String layoutConfigJson = DashboardLayoutCodec.buildLayoutConfigJson(
                dashboard.getLayoutConfig(), spec, finalPanelIds);

        // ===== 阶段 B：写入（校验全部通过后执行） =====
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        IEntityDao<NopDatavPanel> panelDao = daoProvider().daoFor(NopDatavPanel.class);

        // 删除：既有行不在载荷 id 集 → 物理删除；其 AlertRule 置 DISABLED 并注销 cron（§9.2）
        Set<String> payloadIds = new LinkedHashSet<>();
        for (DashboardLayoutCodec.PanelSpec panelSpec : spec.getPanels()) {
            payloadIds.add(panelSpec.getId());
        }
        List<NopDatavPanel> removedPanels = new ArrayList<>();
        for (NopDatavPanel panel : existingPanels) {
            if (!payloadIds.contains(panel.getPanelId())) {
                removedPanels.add(panel);
            }
        }
        if (!removedPanels.isEmpty()) {
            disableAlertRulesForPanels(daoProvider(), removedPanels, operator);
            QueryBean deleteQuery = new QueryBean();
            List<String> removedIds = new ArrayList<>(removedPanels.size());
            for (NopDatavPanel panel : removedPanels) {
                removedIds.add(panel.getPanelId());
            }
            deleteQuery.addFilter(FilterBeans.in("panelId", removedIds));
            panelDao.deleteByQuery(deleteQuery);
        }

        // 更新/新建：sortOrder=数组下标；绑定/tabId 保留（§9.8/9.9）；displayName←title（§9.2）
        for (int i = 0; i < spec.getPanels().size(); i++) {
            DashboardLayoutCodec.PanelSpec panelSpec = spec.getPanels().get(i);
            NopDatavPanel existing = existingById.get(panelSpec.getId());
            if (existing != null) {
                existing.setDisplayName(panelSpec.getTitle());
                existing.setPanelType(panelSpec.getPanelTypeInt());
                existing.setSortOrder(i);
                existing.setPanelConfig(mergedPanelConfigs.get(i));
                existing.setUpdatedBy(operator);
                existing.setUpdateTime(now);
                panelDao.updateEntityDirectly(existing);
            } else {
                NopDatavPanel created = panelDao.newEntity();
                created.setPanelId(finalPanelIds.get(i));
                created.setDashboardId(dashboard.getDashboardId());
                created.setPanelName(panelNames.get(i));
                created.setDisplayName(panelSpec.getTitle());
                created.setPanelType(panelSpec.getPanelTypeInt());
                created.setSortOrder(i);
                created.setPanelConfig(mergedPanelConfigs.get(i));
                created.setDelFlag((byte) 0);
                created.setVersion(0L);
                created.setCreatedBy(operator);
                created.setCreateTime(now);
                created.setUpdatedBy(operator);
                created.setUpdateTime(now);
                panelDao.saveEntityDirectly(created);
            }
        }

        dashboard.setLayoutConfig(layoutConfigJson);
        dashboard.setUpdatedBy(operator);
        dashboard.setUpdateTime(now);
        daoProvider().daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        afterEntityChange(dashboard, "saveDashboardLayout", context);

        // 响应 = 再导出（新建面板携带服务端 id，编辑器重同步身份锚点）
        List<NopDatavPanel> savedPanels = findRelatedEntities(
                NopDatavPanel.class, "dashboardId", id, "sortOrder");
        return DashboardLayoutCodec.buildExportLayout(dashboard.getLayoutConfig(), savedPanels);
    }

    /**
     * 跨看板面板 id 显式报错（§9.2）：载荷 id 未命中本看板面板时，全局查 panelId 归属，
     * 命中其他看板的面板行即抛 ERR_DATAV_LAYOUT_FOREIGN_PANEL_ID（未命中任何行 = 新建，合法）。
     */
    private void checkForeignPanelIds(List<String> unknownIds, String dashboardId) {
        if (unknownIds.isEmpty()) {
            return;
        }
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("panelId", unknownIds));
        List<NopDatavPanel> found = daoProvider().daoFor(NopDatavPanel.class).findAllByQuery(query);
        if (!found.isEmpty()) {
            throw DashboardLayoutCodec.foreignPanelId(found.get(0).getPanelId(), dashboardId);
        }
    }

    /** 布局保存新建面板 id：UUID 去横线 32 字符（panelId VARCHAR(32)，DatavGenerateDashboardExecutor 先例）。 */
    private static String generateLayoutPanelId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private String serializeDashboardContent(NopDatavDashboard dashboard) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dashboardName", dashboard.getDashboardName());
        content.put("displayName", dashboard.getDisplayName());
        content.put("description", dashboard.getDescription());
        content.put("layoutConfig", parseJson(dashboard.getLayoutConfig()));
        content.put("paramConfig", parseJson(dashboard.getParamConfig()));
        content.put("panels", serializePanels(dashboard.getDashboardId()));
        content.put("tabs", serializeTabs(dashboard.getDashboardId()));
        content.put("datasetRefs", serializeDatasetRefs(dashboard.getDashboardId()));
        return JsonTool.stringify(content);
    }

    private List<Map<String, Object>> serializePanels(String dashboardId) {
        List<NopDatavPanel> panels = findRelatedEntities(NopDatavPanel.class, "dashboardId", dashboardId, "sortOrder");
        List<Map<String, Object>> result = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("panelId", panel.getPanelId());
            map.put("panelName", panel.getPanelName());
            map.put("displayName", panel.getDisplayName());
            map.put("panelType", panel.getPanelType());
            map.put("datasetRefId", panel.getDatasetRefId());
            map.put("tabId", panel.getTabId());
            map.put("sortOrder", panel.getSortOrder());
            map.put("panelConfig", parseJson(panel.getPanelConfig()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeTabs(String dashboardId) {
        List<NopDatavDashboardTab> tabs = findRelatedEntities(NopDatavDashboardTab.class, "dashboardId", dashboardId,
                "sortOrder");
        List<Map<String, Object>> result = new ArrayList<>(tabs.size());
        for (NopDatavDashboardTab tab : tabs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tabId", tab.getTabId());
            map.put("tabName", tab.getTabName());
            map.put("displayName", tab.getDisplayName());
            map.put("sortOrder", tab.getSortOrder());
            map.put("tabConfig", parseJson(tab.getTabConfig()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeDatasetRefs(String dashboardId) {
        List<NopDatavDatasetRef> refs = findRelatedEntities(NopDatavDatasetRef.class, "dashboardId", dashboardId, null);
        List<Map<String, Object>> result = new ArrayList<>(refs.size());
        for (NopDatavDatasetRef ref : refs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("datasetRefId", ref.getDatasetRefId());
            map.put("refDatasetId", ref.getRefDatasetId());
            map.put("refDatasetName", ref.getRefDatasetName());
            map.put("paramMapping", parseJson(ref.getParamMapping()));
            result.add(map);
        }
        return result;
    }

    private <T extends IDaoEntity> List<T> findRelatedEntities(Class<T> entityClass, String filterField, String filterValue,
                                            String orderField) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(filterField, filterValue));
        if (orderField != null) {
            query.addOrderField(orderField, false);
        }
        @SuppressWarnings("unchecked")
        List<T> list = (List<T>) daoProvider().daoFor(entityClass).findAllByQuery(query);
        return list;
    }

    private long calculateNextVersion(String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        NopDatavDashboardSnapshot latest = daoProvider()
                .daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
        return latest == null ? 1L : latest.getSnapshotVersion() + 1;
    }

    private NopDatavDashboardSnapshot findLatestSnapshot(String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
    }

    private NopDatavDashboardSnapshot findSnapshotByVersion(String dashboardId, long snapshotVersion) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addFilter(FilterBeans.eq("snapshotVersion", snapshotVersion));
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
    }

    @SuppressWarnings("unchecked")
    private void restoreDashboardFromSnapshot(NopDatavDashboard dashboard, NopDatavDashboardSnapshot snapshot) {
        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        if (content == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_NOT_FOUND)
                    .param("dashboardId", dashboard.getDashboardId());
        }

        Object layoutConfig = content.get("layoutConfig");
        dashboard.setLayoutConfig(layoutConfig == null ? null : JsonTool.stringify(layoutConfig));

        Object paramConfig = content.get("paramConfig");
        dashboard.setParamConfig(paramConfig == null ? null : JsonTool.stringify(paramConfig));

        dashboard.setPublishStatus(PUBLISH_STATUS_PUBLISHED);
        dashboard.setPublishedVersion(snapshot.getSnapshotVersion());
        dashboard.setPublishedBy(snapshot.getPublishedBy());
        dashboard.setPublishedTime(snapshot.getPublishedTime());
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonTool.parse(json);
    }

    private String generateSnapshotId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private void updateDashboardPublishState(String dashboardId, int publishStatus, long publishedVersion,
                                              String publishedBy, Timestamp publishedTime) {
        jdbcTemplate.executeUpdate(SQL.begin().name("updateDashboardPublishState")
                .sql("update NOP_DATAV_DASHBOARD set PUBLISH_STATUS=").param(publishStatus)
                .sql(",PUBLISHED_VERSION=").param(publishedVersion)
                .sql(",PUBLISHED_BY=").param(publishedBy)
                .sql(",PUBLISHED_TIME=").param(publishedTime)
                .sql(" where DASHBOARD_ID=").param(dashboardId).end());
    }

    private void updateDashboardFields(String dashboardId, NopDatavDashboard source) {
        jdbcTemplate.executeUpdate(SQL.begin().name("updateDashboardFields")
                .sql("update NOP_DATAV_DASHBOARD set LAYOUT_CONFIG=").param(source.getLayoutConfig())
                .sql(",PARAM_CONFIG=").param(source.getParamConfig())
                .sql(",PUBLISH_STATUS=").param(source.getPublishStatus())
                .sql(",PUBLISHED_VERSION=").param(source.getPublishedVersion())
                .sql(",PUBLISHED_BY=").param(source.getPublishedBy())
                .sql(",PUBLISHED_TIME=").param(source.getPublishedTime())
                .sql(" where DASHBOARD_ID=").param(dashboardId).end());
    }
}
