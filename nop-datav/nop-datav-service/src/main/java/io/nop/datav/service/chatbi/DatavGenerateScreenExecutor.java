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
import io.nop.datav.biz.ScreenThemeConfig;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.screen.ScreenThemeParser;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static io.nop.datav.service.NopDatavErrors.ARG_COMPONENT_TYPE;
import static io.nop.datav.service.NopDatavErrors.ARG_DATASET_SID;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_SCREEN_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_INVALID_BACKGROUND_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS;

/**
 * ChatBI 大屏生成工具（D6-2）：接收 LLM 产出的结构化大屏规格，校验后事务性创建草稿大屏
 * （Screen + ScreenWidget），返回 screenId。
 *
 * <p>对应工具定义 {@code datav-generate-screen.tool.xml}。设计契约见
 * {@code ai-dev/design/nop-datav/ai-design.md} §9（裁定 L–Q）。</p>
 *
 * <ul>
 *   <li><b>operator 传递</b>（裁定 G，复用 1516-1）：经入参 {@link IToolExecuteContext} 强转为
 *       {@link ChatBiToolExecuteContext} 读取 operator，手动填充实体审计列。</li>
 *   <li><b>datasetRefId 直存 sid</b>（裁定 N）：ScreenWidget.datasetRefId 直接存 nop-report 数据集 sid，
 *       不创建 NopDatavDatasetRef 行，不涉及 ORM 变更。</li>
 *   <li><b>定位校验</b>（裁定 L）：x/y 非负 + w/h > 0 → INVALID_POSITION；越界 → OUT_OF_BOUNDS（mandatory throw，
 *       对齐 screen-design §7.1，确保生成草稿经 getScreenDraftLayout 解析不抛异常）；重叠不校验（watch-only）。</li>
 *   <li><b>装饰组件 datasetSid</b>（裁定 M）：needsDataset=false 组件忽略 datasetSid；needsDataset=true 必填。</li>
 *   <li><b>displayName 回退</b>（裁定 P）：displayName 为空时回退 screenName。</li>
 *   <li><b>screenName UK 冲突</b>（裁定 Q）：捕获 UK 冲突 → DUPLICATE_SCREEN_NAME。</li>
 *   <li><b>事务</b>（裁定 O，复用 1516-1）：{@link IOrmTemplate#runInSession} 包裹；校验全部在创建前先发生（fail-fast）。</li>
 * </ul>
 */
public class DatavGenerateScreenExecutor implements IToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DatavGenerateScreenExecutor.class);

    public static final String TOOL_NAME = "datav-generate-screen";

    public static final int STATUS_ACTIVE = 1;

    public static final int PUBLISH_STATUS_DRAFT = 0;

    public static final int DEFAULT_ADAPTOR_MODE = 10;

    private final PanelComponentRegistry componentRegistry = PanelComponentRegistry.getInstance();
    private final ScreenThemeParser themeParser = new ScreenThemeParser();

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
     * P1-05（plan 2026-08-15-2146-2 Phase 3）：注入事务模板（bean {@code nopTransactionTemplate}），
     * 创建阶段包独立短事务（见 {@link #runCreationInShortTransaction}）。未注入（直调单测）时回退旧行为。
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

            Map<String, Object> spec = parseSpec(call);

            // 1. 基础字段校验
            String screenName = str(spec.get("screenName"));
            if (screenName == null || screenName.isEmpty()) {
                return FutureHelper.success(errorResult(call, new ValidationError(
                        ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "screenName is required")));
            }

            Integer screenWidth = toInt(spec.get("screenWidth"));
            Integer screenHeight = toInt(spec.get("screenHeight"));
            if (screenWidth == null || screenWidth <= 0) {
                return FutureHelper.success(errorResult(call, new ValidationError(
                        ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "screenWidth is required and must be positive")));
            }
            if (screenHeight == null || screenHeight <= 0) {
                return FutureHelper.success(errorResult(call, new ValidationError(
                        ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "screenHeight is required and must be positive")));
            }

            List<?> widgetsSpec = (List<?>) spec.get("widgets");
            if (widgetsSpec == null || widgetsSpec.isEmpty()) {
                return FutureHelper.success(errorResult(call, new ValidationError(
                        ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "widgets is required and must be non-empty")));
            }

            int adaptorMode = toIntOrDefault(spec.get("adaptorMode"), DEFAULT_ADAPTOR_MODE);

            // 裁定 P：displayName 回退 screenName
            String displayName = str(spec.get("displayName"));
            if (displayName == null || displayName.isEmpty()) {
                displayName = screenName;
            }

            // 裁定 N fieldMapping 存储位置：widgetConfig.fieldMapping（在 plan 阶段 buildWidgetPlan 时使用）
            Object backgroundConfigRaw = spec.get("backgroundConfig");

            // 2. 校验阶段（fail-fast）—— 全部校验在创建前完成
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> widgets = (List<Map<String, Object>>) (List<?>) widgetsSpec;

            // 预加载已引用的 dataset sid → NopReportDataset（裁定 N：直存 sid，但需校验存在 + 活跃）
            Set<String> datasetSidsToCheck = new HashSet<>();
            for (Map<String, Object> widgetSpec : widgets) {
                String componentType = str(widgetSpec.get("componentType"));
                if (componentType != null && componentRegistry.isRegistered(componentType)
                        && componentRegistry.requireComponent(componentType).getMetadata().isNeedsDataset()) {
                    String dsid = str(widgetSpec.get("datasetSid"));
                    if (dsid != null && !dsid.isEmpty()) {
                        datasetSidsToCheck.add(dsid);
                    }
                }
            }
            Map<String, NopReportDataset> datasetCache = new HashMap<>();
            for (String dsid : datasetSidsToCheck) {
                NopReportDataset ds = daoProvider.daoFor(NopReportDataset.class).getEntityById(dsid);
                datasetCache.put(dsid, ds);
            }

            List<WidgetPlan> plans = new ArrayList<>();
            for (int i = 0; i < widgets.size(); i++) {
                Object item = widgets.get(i);
                if (!(item instanceof Map)) {
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                            ARG_REASON, "widgets[" + i + "] must be an object")));
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> widgetSpec = (Map<String, Object>) item;

                ValidationError ve = validateAndPlanWidget(widgetSpec, i, screenWidth, screenHeight, datasetCache);
                if (ve != null) {
                    return FutureHelper.success(errorResult(call, ve));
                }
                plans.add(buildWidgetPlan(widgetSpec, i));
            }

            // 校验 backgroundConfig（裁定：经 ScreenThemeParser 可解析）
            Map<String, Object> backgroundConfig = null;
            if (backgroundConfigRaw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bgMap = (Map<String, Object>) backgroundConfigRaw;
                backgroundConfig = bgMap;
                try {
                    // 经 ScreenThemeParser 校验可解析（含 palette/background 结构合法性）
                    ScreenThemeConfig theme = themeParser.resolve(null, backgroundConfig);
                    if (theme == null) {
                        return FutureHelper.success(errorResult(call, new ValidationError(
                                ERR_DATAV_CHATBI_GENERATE_INVALID_BACKGROUND_CONFIG,
                                ARG_REASON, "backgroundConfig resolved to null theme")));
                    }
                } catch (NopException e) {
                    LOG.warn("backgroundConfig is not parseable as screen theme", e);
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_INVALID_BACKGROUND_CONFIG,
                            ARG_REASON, "backgroundConfig is not parseable as screen theme")));
                }
            }

            // 裁定 Q：screenName UK 冲突预检查（查询优先，比依赖 DB UK 异常更健壮：
            // 测试库 DdlSqlCreator 可能不建 UK 索引；预查在任何环境下一致工作）
            if (existsScreenByName(screenName)) {
                return FutureHelper.success(errorResult(call, new ValidationError(
                        ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME,
                        ARG_SCREEN_NAME, screenName)));
            }

            // 3. 创建阶段（裁定 O，事务包裹；P1-05：无 ambient 事务时开独立短事务即时提交，
            // 有 ambient 事务时加入（与修复前行为一致））
            final String fDisplayName = displayName;
            final int fAdaptorMode = adaptorMode;
            final Map<String, Object> fBackgroundConfig = backgroundConfig;
            final String fScreenName = screenName;
            try {
                CreationResult created = runCreationInShortTransaction(session ->
                        doCreate(fScreenName, fDisplayName, screenWidth, screenHeight, fAdaptorMode,
                                fBackgroundConfig, plans, operator, session));

                String json = JsonTool.stringify(buildResultJson(created));
                return FutureHelper.success(AiToolCallResult.successResult(call.getId(), json));
            } catch (NopException e) {
                if (ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME.getErrorCode().equals(e.getErrorCode())) {
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME,
                            ARG_SCREEN_NAME, screenName)));
                }
                return FutureHelper.success(errorResultFromException(call, e));
            } catch (Exception e) {
                // 捕获底层 UK 冲突（不同 DB 驱动的异常类型不固定，经错误码/消息匹配）
                if (isUniqueConstraintViolation(e)) {
                    return FutureHelper.success(errorResult(call, new ValidationError(
                            ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME,
                            ARG_SCREEN_NAME, screenName)));
                }
                return FutureHelper.success(AiToolCallResult.errorResult(call.getId(), e.toString()));
            }
        } catch (NopException e) {
            return FutureHelper.success(errorResultFromException(call, e));
        } catch (Exception e) {
            return FutureHelper.success(AiToolCallResult.errorResult(call.getId(), e.toString()));
        }
    }

    private ValidationError validateAndPlanWidget(Map<String, Object> widgetSpec, int index,
                                                    int canvasWidth, int canvasHeight,
                                                    Map<String, NopReportDataset> datasetCache) {
        String componentType = str(widgetSpec.get("componentType"));
        if (componentType == null || componentType.isEmpty()) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                    ARG_REASON, "widgets[" + index + "].componentType is required");
        }

        // 大屏可用全部 14 类（裁定：不经 PanelTypeMapping 限制）
        if (!componentRegistry.isRegistered(componentType)) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT,
                    ARG_COMPONENT_TYPE, componentType);
        }

        boolean needsDataset = componentRegistry.requireComponent(componentType)
                .getMetadata().isNeedsDataset();
        String datasetSid = str(widgetSpec.get("datasetSid"));

        // 裁定 M：needsDataset 处理
        if (needsDataset) {
            if (datasetSid == null || datasetSid.isEmpty()) {
                return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                        ARG_REASON, "widgets[" + index + "] componentType=" + componentType
                                + " needs a dataset but datasetSid is missing");
            }
            NopReportDataset ds = datasetCache.get(datasetSid);
            // 也可能没预加载（needsDataset 但未在预扫中），补查一次
            if (ds == null) {
                ds = daoProvider.daoFor(NopReportDataset.class).getEntityById(datasetSid);
            }
            if (ds == null || ds.getStatus() == null || ds.getStatus() != STATUS_ACTIVE) {
                return new ValidationError(ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND,
                        ARG_DATASET_SID, datasetSid);
            }
            // 裁定 N：fieldMapping 字段名校验
            Object fmVal = widgetSpec.get("fieldMapping");
            if (fmVal instanceof Map) {
                Set<String> fieldNames = DatasetMetaParser.parseFieldNames(ds.getDsMeta());
                for (Object v : ((Map<String, Object>) fmVal).values()) {
                    if (v == null) {
                        continue;
                    }
                    String refField = v.toString();
                    if (!refField.isEmpty() && !fieldNames.contains(refField)) {
                        return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                                ARG_REASON, "widgets[" + index + "] fieldMapping references field '"
                                        + refField + "' not present in dataset " + datasetSid
                                        + " dsMeta field set");
                    }
                }
            }
        }

        // 裁定 L：定位校验
        Integer x = toInt(widgetSpec.get("x"));
        Integer y = toInt(widgetSpec.get("y"));
        Integer w = toInt(widgetSpec.get("w"));
        Integer h = toInt(widgetSpec.get("h"));

        if (x == null || y == null || w == null || h == null) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC,
                    ARG_REASON, "widgets[" + index + "] x/y/w/h are all required");
        }
        if (x < 0 || y < 0 || w <= 0 || h <= 0) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION,
                    ARG_REASON, "widgets[" + index + "] invalid position: x=" + x + " y=" + y
                            + " w=" + w + " h=" + h + " (x/y must be >= 0, w/h must be > 0)");
        }
        // 越界 mandatory throw（对齐 screen-design §7.1）
        if (x + w > canvasWidth || y + h > canvasHeight) {
            return new ValidationError(ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS,
                    ARG_REASON, "widgets[" + index + "] out of bounds: x+w=" + (x + w)
                            + " > screenWidth=" + canvasWidth + " or y+h=" + (y + h)
                            + " > screenHeight=" + canvasHeight);
        }
        // 重叠不校验（watch-only，§7.3 装饰层叠合法）
        return null;
    }

    private WidgetPlan buildWidgetPlan(Map<String, Object> widgetSpec, int index) {
        WidgetPlan plan = new WidgetPlan();
        plan.componentType = str(widgetSpec.get("componentType"));
        plan.needsDataset = componentRegistry.requireComponent(plan.componentType)
                .getMetadata().isNeedsDataset();
        plan.datasetSid = str(widgetSpec.get("datasetSid"));
        plan.fieldMapping = widgetSpec.get("fieldMapping");
        plan.x = toInt(widgetSpec.get("x"));
        plan.y = toInt(widgetSpec.get("y"));
        plan.w = toInt(widgetSpec.get("w"));
        plan.h = toInt(widgetSpec.get("h"));
        plan.z = toIntOrDefault(widgetSpec.get("z"), 0);
        plan.widgetName = "widget-" + index;
        return plan;
    }

    /**
     * P1-05 修复（plan 2026-08-15-2146-2 Phase 3，镜像 DatavGenerateDashboardExecutor）：创建阶段包
     * 独立短事务——ChatBI 循环移出事务后，多表写（Screen + ScreenWidget）若无事务则逐语句 auto-commit
     * 失去原子性（半成品大屏风险）。REQUIRED 传播：无 ambient 事务时新开短事务（创建即提交，screenId
     * 对后续轮次可读），有 ambient 事务时加入（与修复前行为一致）。{@code transactionTemplate} 未注入
     * （直调单测）时回退 {@code runInSession}（回归兼容）。
     */
    private CreationResult runCreationInShortTransaction(java.util.function.Function<IOrmSession, CreationResult> body) {
        if (transactionTemplate == null) {
            return ormTemplate.runInSession(body);
        }
        return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED,
                txn -> ormTemplate.runInSession(body));
    }

    private CreationResult doCreate(String screenName, String displayName, int screenWidth, int screenHeight,
                                      int adaptorMode, Map<String, Object> backgroundConfig,
                                      List<WidgetPlan> plans, String operator, IOrmSession session) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        IEntityDao<NopDatavScreen> screenDao = daoProvider.daoFor(NopDatavScreen.class);
        IEntityDao<NopDatavScreenWidget> widgetDao = daoProvider.daoFor(NopDatavScreenWidget.class);

        String screenId = generateId();

        // 1. Screen（DRAFT, createdBy=operator 手动填，displayName 已回退）
        NopDatavScreen screen = screenDao.newEntity();
        screen.setScreenId(screenId);
        screen.setScreenName(screenName);
        screen.setDisplayName(displayName);
        screen.setScreenWidth(screenWidth);
        screen.setScreenHeight(screenHeight);
        screen.setAdaptorMode(adaptorMode);
        if (backgroundConfig != null) {
            screen.setBackgroundConfig(JsonTool.stringify(backgroundConfig));
        }
        screen.setPublishStatus(PUBLISH_STATUS_DRAFT);
        screen.setPublishedVersion(0L);
        screen.setDelFlag((byte) 0);
        screen.setVersion(0L);
        screen.setCreatedBy(operator);
        screen.setCreateTime(now);
        screen.setUpdatedBy(operator);
        screen.setUpdateTime(now);
        screenDao.saveEntityDirectly(screen);

        // 2. ScreenWidget 集合
        List<WidgetSummary> widgetSummaries = new ArrayList<>();
        for (WidgetPlan plan : plans) {
            NopDatavScreenWidget widget = widgetDao.newEntity();
            widget.setWidgetId(generateId());
            widget.setScreenId(screenId);
            widget.setWidgetName(plan.widgetName);
            widget.setDisplayName(plan.widgetName);
            // componentType string 直存（screen-design §10.5）
            widget.setComponentType(plan.componentType);
            // 裁定 N：datasetRefId 直存 nop-report sid（不经 DatasetRef）
            // 裁定 M：needsDataset=false 组件 datasetRefId 为 null
            if (plan.needsDataset && plan.datasetSid != null) {
                widget.setDatasetRefId(plan.datasetSid);
            } else {
                widget.setDatasetRefId(null);
            }
            widget.setX(plan.x);
            widget.setY(plan.y);
            widget.setW(plan.w);
            widget.setH(plan.h);
            widget.setZ(plan.z);
            // 裁定 N：fieldMapping 存入 widgetConfig.fieldMapping
            if (plan.fieldMapping != null) {
                Map<String, Object> widgetConfig = new LinkedHashMap<>();
                widgetConfig.put("fieldMapping", plan.fieldMapping);
                widget.setWidgetConfig(JsonTool.stringify(widgetConfig));
            }
            widget.setDelFlag((byte) 0);
            widget.setVersion(0L);
            widget.setCreatedBy(operator);
            widget.setCreateTime(now);
            widget.setUpdatedBy(operator);
            widget.setUpdateTime(now);
            widgetDao.saveEntityDirectly(widget);

            widgetSummaries.add(new WidgetSummary(plan.widgetName, plan.componentType,
                    plan.needsDataset ? plan.datasetSid : null,
                    plan.x, plan.y, plan.w, plan.h, plan.z));
        }

        return new CreationResult(screenId, widgetSummaries);
    }

    private Map<String, Object> buildResultJson(CreationResult created) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("screenId", created.screenId);

        List<Map<String, Object>> widgetsJson = new ArrayList<>();
        for (WidgetSummary w : created.widgets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("widgetName", w.widgetName);
            m.put("componentType", w.componentType);
            m.put("datasetRefId", w.datasetSid);
            m.put("x", w.x);
            m.put("y", w.y);
            m.put("w", w.w);
            m.put("h", w.h);
            m.put("z", w.z);
            widgetsJson.add(m);
        }
        result.put("widgets", widgetsJson);
        return result;
    }

    private String resolveOperator(IToolExecuteContext context) {
        if (context instanceof ChatBiToolExecuteContext) {
            String op = ((ChatBiToolExecuteContext) context).getOperator();
            if (op != null && !op.isEmpty()) {
                return op;
            }
        }
        return NopDatavOperatorResolver.SYSTEM_OPERATOR;
    }

    /**
     * 裁定 Q：检查同名大屏是否已存在（预查，比依赖 DB UK 异常更健壮）。
     */
    private boolean existsScreenByName(String screenName) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("screenName", screenName));
        query.setLimit(1);
        return daoProvider.daoFor(NopDatavScreen.class).findFirstByQuery(query) != null;
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
                + ", screenName=" + error.screenName
                + ", reason=" + error.reason + ")";
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int toIntOrDefault(Object v, int defaultValue) {
        Integer i = toInt(v);
        return i == null ? defaultValue : i;
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static boolean isUniqueConstraintViolation(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase();
        // AR-3: 只匹配 UK 特有子串。H2 "Unique index or primary key violation"、
        // MySQL "Duplicate entry"、PG "duplicate key value violates unique constraint" 均命中。
        // 不再用 "constraint"/"uk_"，否则会把 CHECK/FK/NOT NULL 等非 UK 约束违错误报为重名，
        // 触发 LLM 无意义的 rename-retry 循环。
        return lower.contains("unique") || lower.contains("duplicate");
    }

    // ==================== 内部辅助类 ====================

    private static final class ValidationError {
        final io.nop.api.core.exceptions.ErrorCode code;
        final String componentType;
        final String datasetSid;
        final String screenName;
        final String reason;

        ValidationError(io.nop.api.core.exceptions.ErrorCode code, String key, String value) {
            this.code = code;
            if (ARG_COMPONENT_TYPE.equals(key)) {
                this.componentType = value;
                this.datasetSid = null;
                this.screenName = null;
                this.reason = null;
            } else if (ARG_DATASET_SID.equals(key)) {
                this.componentType = null;
                this.datasetSid = value;
                this.screenName = null;
                this.reason = null;
            } else if (ARG_SCREEN_NAME.equals(key)) {
                this.componentType = null;
                this.datasetSid = null;
                this.screenName = value;
                this.reason = null;
            } else {
                this.componentType = null;
                this.datasetSid = null;
                this.screenName = null;
                this.reason = value;
            }
        }
    }

    private static final class WidgetPlan {
        String componentType;
        boolean needsDataset;
        String datasetSid;
        Object fieldMapping;
        int x;
        int y;
        int w;
        int h;
        int z;
        String widgetName;
    }

    private static final class CreationResult {
        final String screenId;
        final List<WidgetSummary> widgets;

        CreationResult(String screenId, List<WidgetSummary> widgets) {
            this.screenId = screenId;
            this.widgets = Collections.unmodifiableList(new ArrayList<>(widgets));
        }
    }

    private static final class WidgetSummary {
        final String widgetName;
        final String componentType;
        final String datasetSid;
        final int x;
        final int y;
        final int w;
        final int h;
        final int z;

        WidgetSummary(String widgetName, String componentType, String datasetSid,
                       int x, int y, int w, int h, int z) {
            this.widgetName = widgetName;
            this.componentType = componentType;
            this.datasetSid = datasetSid;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.z = z;
        }
    }
}
