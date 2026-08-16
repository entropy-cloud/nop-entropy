package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.component.PanelTypeMapping;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.nop.datav.service.NopDatavErrors.ARG_COMPONENT_TYPE;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_NAME;
import static io.nop.datav.service.NopDatavErrors.ARG_DATASET_SID;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_DATASET_NO_ACCESS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_DUPLICATE_DASHBOARD_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT;

/**
 * ChatBI 看板生成工具（D6-1b）：接收 LLM 产出的结构化看板规格，校验后事务性创建草稿看板
 * （Dashboard + DatasetRef + Panel），返回 dashboardId。
 *
 * <p>对应工具定义 {@code datav-generate-dashboard.tool.xml}。设计契约见
 * {@code ai-dev/design/nop-datav/ai-design.md} §8（裁定 G–O）。</p>
 *
 * <ul>
 *   <li><b>operator 传递</b>（裁定 G）：经入参 {@link IToolExecuteContext} 强转为
 *       {@link ChatBiToolExecuteContext} 读取 operator，手动填充实体审计列
 *       （{@code createdBy}/{@code updatedBy}）。tool executor 无 BizModel 用户上下文，
 *       审计列必须手动填充，否则 {@code createdBy} 为 null。</li>
 *   <li><b>校验</b>（裁定 J/M/N + AR-1 可见性）：componentType 属 8 类可生成类型；needsDataset=true 组件 datasetSid
 *       必填且指向 status=1 的 NopReportDataset，且对当前身份（{@link ChatBiDatasetVisibility}，AR-1 修复
 *       plan 2026-08-16-2137-1）可见——admin 全量，非 admin 仅 createdBy 匹配，不可见显式拒绝
 *       {@code ERR_DATAV_CHATBI_DATASET_NO_ACCESS}；fieldMapping 引用字段 ∈ dsMeta 字段名集合。</li>
 *   <li><b>DatasetRef 去重</b>（裁定 I）：同 dashboard 内同 refDatasetId 复用一个 DatasetRef，
 *       paramMapping 初值 {@code {}}。</li>
 *   <li><b>事务</b>（裁定 O）：{@link IOrmTemplate#runInSession} 包裹多表创建；校验全部在创建前先发生
 *       （fail-fast），失败时不落任何行（无半成品）。</li>
 * </ul>
 */
public class DatavGenerateDashboardExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "datav-generate-dashboard";

    /**
     * status=1 对应 dict {@code core/active-status} 的"活跃"状态（与 DatavListDatasetsExecutor 一致）。
     */
    public static final int STATUS_ACTIVE = 1;

    public static final int PUBLISH_STATUS_DRAFT = 0;

    private static final String PARAM_MAPPING_EMPTY_JSON = "{}";

    private final PanelComponentRegistry componentRegistry = PanelComponentRegistry.getInstance();

    private IDaoProvider daoProvider;
    private IOrmTemplate ormTemplate;
    private ITransactionTemplate transactionTemplate;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    /**
     * P1-05：注入事务模板（bean {@code nopTransactionTemplate}），创建阶段包独立短事务（见
     * {@link #runCreationInShortTransaction}）。未注入（直调单测）时回退旧行为。
     */
    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            String operator = resolveOperator(context);

            // AR-1（plan 2026-08-16-2137-1）：数据集可见性身份取 ChatBiDatasetVisibility（fail-closed，
            // 无 SYSTEM_OPERATOR 回退）——与本类 resolveOperator（仅用于落 createdBy，回退 "system"）分离，
            // 否则 createdBy="system" 的数据集会对所有人可见。
            String visOperator = ChatBiDatasetVisibility.resolveOperator(context);
            boolean visAdmin = ChatBiDatasetVisibility.resolveAdmin(context);

            Map<String, Object> spec = parseSpec(call);
            List<ValidationError> errors = new ArrayList<>();

            String dashboardName = str(spec.get("dashboardName"));
            if (dashboardName == null || dashboardName.isEmpty()) {
                errors.add(new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "dashboardName is required"));
            }

            List<?> panelsSpec = (List<?>) spec.get("panels");
            if (panelsSpec == null || panelsSpec.isEmpty()) {
                errors.add(new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "panels is required and must be non-empty"));
            }

            if (!errors.isEmpty()) {
                return FutureHelper.success(errorResult(call, errors.get(0)));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> panels = (List<Map<String, Object>>) (List<?>) panelsSpec;

            // 校验阶段（裁定 J/M/N）—— 全部校验在创建前完成（fail-fast，裁定 O）
            List<PanelPlan> plans = new ArrayList<>();
            Set<String> seenDatasetSids = new HashSet<>();
            for (int i = 0; i < panels.size(); i++) {
                Object item = panels.get(i);
                if (!(item instanceof Map)) {
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                            ARG_REASON, "panels[" + i + "] must be an object")));
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> panelSpec = (Map<String, Object>) item;

                ValidationError ve = validateAndPlanPanel(panelSpec, i, daoProvider, seenDatasetSids,
                        visOperator, visAdmin);
                if (ve != null) {
                    return FutureHelper.success(errorResult(call, ve));
                }
                plans.add(buildPanelPlan(panelSpec, i));
            }

            // D1(b)（plan 2026-08-15-2146-3 Phase 2，镜像 Screen executor 裁定 Q）：dashboardName UK
            // 冲突预检查（查询优先，比依赖 DB UK 异常更健壮；预查在任何环境下一致工作）
            if (existsDashboardByName(dashboardName)) {
                return FutureHelper.success(errorResult(call, new ValidationError(
                        ERR_DATAV_CHATBI_GENERATE_DUPLICATE_DASHBOARD_NAME,
                        ARG_DASHBOARD_NAME, dashboardName)));
            }

            // 创建阶段（裁定 O，事务包裹；P1-05：无 ambient 事务时开独立短事务即时提交，
            // 有 ambient 事务时加入（与修复前行为一致））
            try {
                CreationResult created = runCreationInShortTransaction(session ->
                        doCreate(dashboardName, str(spec.get("description")), plans, operator, session));

                String json = JsonTool.stringify(buildResultJson(created));
                return FutureHelper.success(AiToolCallResult.successResult(call.getId(), json));
            } catch (NopException e) {
                if (ERR_DATAV_CHATBI_GENERATE_DUPLICATE_DASHBOARD_NAME.getErrorCode().equals(e.getErrorCode())) {
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_DUPLICATE_DASHBOARD_NAME,
                            ARG_DASHBOARD_NAME, dashboardName)));
                }
                return FutureHelper.success(errorResultFromException(call, e));
            } catch (Exception e) {
                // 捕获底层 UK 冲突（预查与插入之间的并发窗口；判定逻辑与 Screen executor 共用）
                if (DatavGenerateScreenExecutor.isUniqueConstraintViolation(e)) {
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_DUPLICATE_DASHBOARD_NAME,
                            ARG_DASHBOARD_NAME, dashboardName)));
                }
                return FutureHelper.success(AiToolCallResult.errorResult(call.getId(), e.toString()));
            }
        } catch (NopException e) {
            return FutureHelper.success(errorResultFromException(call, e));
        } catch (Exception e) {
            return FutureHelper.success(AiToolCallResult.errorResult(call.getId(), e.toString()));
        }
    }

    private String resolveOperator(IToolExecuteContext context) {
        // 裁定 G：ChatBI 循环传入的是 ChatBiToolExecuteContext（携带 operator）。强转耦合契约。
        if (context instanceof ChatBiToolExecuteContext) {
            String op = ((ChatBiToolExecuteContext) context).getOperator();
            if (op != null && !op.isEmpty()) {
                return op;
            }
        }
        return NopDatavOperatorResolver.SYSTEM_OPERATOR;
    }

    /**
     * P1-05 修复（plan 2026-08-15-2146-2 Phase 3）：创建阶段包独立短事务——
     * ChatBI 循环移出事务后（BizModel 经 runWithoutTransaction 挂起 ambient 事务），本 executor 的
     * 多表写（Dashboard + DatasetRef + Panel）若无事务则以逐语句 auto-commit 落库，失去原子性
     * （半成品看板风险，裁定 O 前提被破坏）。REQUIRED 传播：无 ambient 事务时新开短事务
     * （创建即提交，dashboardId 对后续轮次可读），有 ambient 事务时加入（与修复前行为一致）。
     * {@code transactionTemplate} 未注入（直调单测）时回退 {@code runInSession}（回归兼容）。
     */
    private CreationResult runCreationInShortTransaction(Function<IOrmSession, CreationResult> body) {
        if (transactionTemplate == null) {
            return ormTemplate.runInSession(body);
        }
        return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED,
                txn -> ormTemplate.runInSession(body));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSpec(AiToolCall call) {
        String inputText = call.getInput();
        if (inputText == null || inputText.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JsonTool.parseNonStrict(inputText);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        return new LinkedHashMap<>();
    }

    private ValidationError validateAndPlanPanel(Map<String, Object> panelSpec, int index,
                                                  IDaoProvider dp, Set<String> seenDatasetSidsCache,
                                                  String visOperator, boolean visAdmin) {
        String title = str(panelSpec.get("title"));
        if (title == null || title.isEmpty()) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                    ARG_REASON, "panels[" + index + "].title is required");
        }

        String componentType = str(panelSpec.get("componentType"));
        if (componentType == null || componentType.isEmpty()) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                    ARG_REASON, "panels[" + index + "].componentType is required");
        }

        // 裁定 M：componentType 校验。先判 14 类注册表，再判 8 类 panelType 映射。
        if (!componentRegistry.isRegistered(componentType)) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT,
                    ARG_COMPONENT_TYPE, componentType);
        }
        int panelType;
        try {
            panelType = PanelTypeMapping.toPanelTypeInt(componentType);
        } catch (NopException e) {
            // 注册表内但无 panelType int 映射 → 装饰类型（大屏专用），看板生成拒绝
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT,
                    ARG_COMPONENT_TYPE, componentType);
        }

        boolean needsDataset = componentRegistry.requireComponent(componentType)
                .getMetadata().isNeedsDataset();
        String datasetSid = str(panelSpec.get("datasetSid"));

        if (needsDataset) {
            if (datasetSid == null || datasetSid.isEmpty()) {
                return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "panels[" + index + "] componentType=" + componentType
                                + " needs a dataset but datasetSid is missing");
            }
            // AR-1 修复（plan 2026-08-16-2137-1，镜像 describe/query 先例）：加载 + 校验 status=1（活跃）
            // + 可见性（P1-03 裁定 D4 选项 B）。nop-report 数据集无 DAO 层 RLS（nop-report data-auth 为空），
            // 可见性由本 executor 显式实施：admin 全量；非 admin 仅 createdBy 匹配当前 operator；
            // 无身份（operator 空且非 admin）fail-closed 同样不可见。不可见显式拒绝（非静默、非 NOT_FOUND
            // 误报），与既有 NOT_FOUND 分工：不存在/非活跃 → GENERATE_DATASET_NOT_FOUND。
            NopReportDataset ds = dp.daoFor(NopReportDataset.class).getEntityById(datasetSid);
            if (ds == null || ds.getStatus() == null || ds.getStatus() != STATUS_ACTIVE) {
                return new ValidationError(ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND,
                        ARG_DATASET_SID, datasetSid);
            }
            if (!ChatBiDatasetVisibility.isVisible(ds, visOperator, visAdmin)) {
                return ValidationError.datasetNoAccess(datasetSid, visOperator);
            }
            // 裁定 N：fieldMapping 字段名校验
            Object fmVal = panelSpec.get("fieldMapping");
            if (fmVal instanceof Map) {
                Set<String> fieldNames = DatasetMetaParser.parseFieldNames(ds.getDsMeta());
                for (Object v : ((Map<String, Object>) fmVal).values()) {
                    if (v == null) {
                        continue;
                    }
                    String refField = v.toString();
                    if (!refField.isEmpty() && !fieldNames.contains(refField)) {
                        return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                                ARG_REASON, "panels[" + index + "] fieldMapping references field '"
                                        + refField + "' not present in dataset " + datasetSid
                                        + " dsMeta field set");
                    }
                }
            }
        }
        return null;
    }

    private PanelPlan buildPanelPlan(Map<String, Object> panelSpec, int index) {
        PanelPlan plan = new PanelPlan();
        plan.title = str(panelSpec.get("title"));
        plan.componentType = str(panelSpec.get("componentType"));
        plan.panelType = PanelTypeMapping.toPanelTypeInt(plan.componentType);
        plan.needsDataset = componentRegistry.requireComponent(plan.componentType)
                .getMetadata().isNeedsDataset();
        plan.datasetSid = str(panelSpec.get("datasetSid"));
        plan.fieldMapping = panelSpec.get("fieldMapping");
        Object so = panelSpec.get("sortOrder");
        plan.sortOrder = so instanceof Number ? ((Number) so).intValue() : index;
        return plan;
    }

    private CreationResult doCreate(String dashboardName, String description, List<PanelPlan> plans,
                                     String operator, IOrmSession session) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        IEntityDao<NopDatavDashboard> dashDao = daoProvider.daoFor(NopDatavDashboard.class);
        IEntityDao<NopDatavDatasetRef> refDao = daoProvider.daoFor(NopDatavDatasetRef.class);
        IEntityDao<NopDatavPanel> panelDao = daoProvider.daoFor(NopDatavPanel.class);
        IEntityDao<NopReportDataset> reportDao = daoProvider.daoFor(NopReportDataset.class);

        String dashboardId = generateId();

        // 1. Dashboard（裁定 H：DRAFT, version=0；裁定 G：createdBy=operator 手动填）
        NopDatavDashboard dashboard = dashDao.newEntity();
        dashboard.setDashboardId(dashboardId);
        dashboard.setDashboardName(dashboardName);
        dashboard.setDisplayName(dashboardName);
        if (description != null) {
            dashboard.setDescription(description);
        }
        dashboard.setPublishStatus(PUBLISH_STATUS_DRAFT);
        dashboard.setPublishedVersion(0L);
        dashboard.setDelFlag((byte) 0);
        dashboard.setVersion(0L);
        dashboard.setCreatedBy(operator);
        dashboard.setCreateTime(now);
        dashboard.setUpdatedBy(operator);
        dashboard.setUpdateTime(now);
        dashDao.saveEntityDirectly(dashboard);

        // 2. DatasetRef 去重（裁定 I）: 同 dashboard + 同 refDatasetId → 一个 DatasetRef
        Map<String, String> datasetSidToRefId = new HashMap<>();
        List<DatasetRefSummary> refSummaries = new ArrayList<>();

        // 3. Panel 集合
        List<PanelSummary> panelSummaries = new ArrayList<>();
        for (PanelPlan plan : plans) {
            String datasetRefId = null;
            if (plan.needsDataset && plan.datasetSid != null) {
                datasetRefId = datasetSidToRefId.get(plan.datasetSid);
                if (datasetRefId == null) {
                    NopReportDataset ds = reportDao.getEntityById(plan.datasetSid);
                    NopDatavDatasetRef ref = refDao.newEntity();
                    String refId = generateId();
                    ref.setDatasetRefId(refId);
                    ref.setDashboardId(dashboardId);
                    ref.setRefDatasetId(plan.datasetSid);
                    ref.setRefDatasetName(ds != null ? ds.getDsName() : plan.datasetSid);
                    ref.setParamMapping(PARAM_MAPPING_EMPTY_JSON);
                    ref.setDelFlag((byte) 0);
                    ref.setVersion(0L);
                    ref.setCreatedBy(operator);
                    ref.setCreateTime(now);
                    ref.setUpdatedBy(operator);
                    ref.setUpdateTime(now);
                    refDao.saveEntityDirectly(ref);

                    datasetSidToRefId.put(plan.datasetSid, refId);
                    datasetRefId = refId;
                    refSummaries.add(new DatasetRefSummary(refId, plan.datasetSid));
                }
            }

            NopDatavPanel panel = panelDao.newEntity();
            panel.setPanelId(generateId());
            panel.setDashboardId(dashboardId);
            panel.setPanelName(plan.title); // mandatory 列
            panel.setDisplayName(plan.title);
            panel.setPanelType(plan.panelType);
            panel.setDatasetRefId(datasetRefId);
            panel.setSortOrder(plan.sortOrder);
            // panelConfig 存 fieldMapping（供前端消费，后端 getPanelData 不读 panelConfig/fieldMapping）
            if (plan.fieldMapping != null) {
                Map<String, Object> panelConfig = new LinkedHashMap<>();
                panelConfig.put("fieldMapping", plan.fieldMapping);
                panel.setPanelConfig(JsonTool.stringify(panelConfig));
            }
            panel.setDelFlag((byte) 0);
            panel.setVersion(0L);
            panel.setCreatedBy(operator);
            panel.setCreateTime(now);
            panel.setUpdatedBy(operator);
            panel.setUpdateTime(now);
            panelDao.saveEntityDirectly(panel);

            panelSummaries.add(new PanelSummary(plan.title, plan.componentType, datasetRefId));
        }

        return new CreationResult(dashboardId, panelSummaries, refSummaries);
    }

    private Map<String, Object> buildResultJson(CreationResult created) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboardId", created.dashboardId);

        List<Map<String, Object>> panelsJson = new ArrayList<>();
        for (PanelSummary p : created.panels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("panelName", p.panelName);
            m.put("componentType", p.componentType);
            m.put("datasetRefId", p.datasetRefId);
            panelsJson.add(m);
        }
        result.put("panels", panelsJson);

        List<Map<String, Object>> refsJson = new ArrayList<>();
        for (DatasetRefSummary r : created.refs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("datasetRefId", r.datasetRefId);
            m.put("refDatasetId", r.refDatasetId);
            refsJson.add(m);
        }
        result.put("datasetRefs", refsJson);
        return result;
    }

    private static AiToolCallResult errorResult(AiToolCall call, ValidationError error) {
        return AiToolCallResult.errorResult(call.getId(), formatValidationError(error));
    }

    private static AiToolCallResult errorResultFromException(AiToolCall call, NopException e) {
        return AiToolCallResult.errorResult(call.getId(),
                "Error: " + e.getErrorCode() + " | " + e.getMessage());
    }

    private static String formatValidationError(ValidationError error) {
        return "Error: " + error.code.getErrorCode()
                + " (componentType=" + error.componentType
                + ", datasetSid=" + error.datasetSid
                + ", dashboardName=" + error.dashboardName
                + ", userName=" + error.userName
                + ", reason=" + error.reason + ")";
    }

    /**
     * D1(b)：检查同名看板是否已存在（预查，比依赖 DB UK 异常更健壮；镜像 Screen executor 裁定 Q）。
     */
    private boolean existsDashboardByName(String dashboardName) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("dashboardName", dashboardName));
        query.setLimit(1);
        return daoProvider.daoFor(NopDatavDashboard.class).findFirstByQuery(query) != null;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static String generateId() {
        // ID 列 precision=32（VARCHAR(32)）。UUID 去横线恰好 32 字符，不加前缀（前缀会超长）。
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== 内部辅助类 ====================

    private static final class ValidationError {
        final io.nop.api.core.exceptions.ErrorCode code;
        final String componentType;
        final String datasetSid;
        final String dashboardName;
        final String reason;
        /**
         * AR-1（plan 2026-08-16-2137-1）：仅 NO_ACCESS 错误填充（镜像 describe/query 先例的错误体三要素
         * errorCode + datasetSid + userName），其余错误保持 null。
         */
        String userName;

        ValidationError(io.nop.api.core.exceptions.ErrorCode code, String key, String value) {
            this.code = code;
            if (ARG_COMPONENT_TYPE.equals(key)) {
                this.componentType = value;
                this.datasetSid = null;
                this.dashboardName = null;
                this.reason = null;
            } else if (ARG_DATASET_SID.equals(key)) {
                this.componentType = null;
                this.datasetSid = value;
                this.dashboardName = null;
                this.reason = null;
            } else if (ARG_DASHBOARD_NAME.equals(key)) {
                this.componentType = null;
                this.datasetSid = null;
                this.dashboardName = value;
                this.reason = null;
            } else {
                this.componentType = null;
                this.datasetSid = null;
                this.dashboardName = null;
                this.reason = value;
            }
        }

        /** AR-1：不可见拒绝错误体（三要素：errorCode + datasetSid + userName，userName 空时 "<null>"）。 */
        static ValidationError datasetNoAccess(String datasetSid, String userName) {
            ValidationError error = new ValidationError(ERR_DATAV_CHATBI_DATASET_NO_ACCESS,
                    ARG_DATASET_SID, datasetSid);
            error.userName = userName != null ? userName : "<null>";
            return error;
        }
    }

    private static final class PanelPlan {
        String title;
        String componentType;
        int panelType;
        boolean needsDataset;
        String datasetSid;
        Object fieldMapping;
        int sortOrder;
    }

    private static final class CreationResult {
        final String dashboardId;
        final List<PanelSummary> panels;
        final List<DatasetRefSummary> refs;

        CreationResult(String dashboardId, List<PanelSummary> panels, List<DatasetRefSummary> refs) {
            this.dashboardId = dashboardId;
            this.panels = Collections.unmodifiableList(new ArrayList<>(panels));
            this.refs = Collections.unmodifiableList(new ArrayList<>(refs));
        }
    }

    private static final class PanelSummary {
        final String panelName;
        final String componentType;
        final String datasetRefId;

        PanelSummary(String panelName, String componentType, String datasetRefId) {
            this.panelName = panelName;
            this.componentType = componentType;
            this.datasetRefId = datasetRefId;
        }
    }

    private static final class DatasetRefSummary {
        final String datasetRefId;
        final String refDatasetId;

        DatasetRefSummary(String datasetRefId, String refDatasetId) {
            this.datasetRefId = datasetRefId;
            this.refDatasetId = refDatasetId;
        }
    }
}
