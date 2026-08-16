package io.nop.datav.service.entity;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolCallInterceptor;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.ai.toolkit.manager.ToolManagerImpl;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.DatavGenerateDashboardExecutor;
import io.nop.datav.service.chatbi.DatavGenerateScreenExecutor;
import io.nop.orm.IOrmTemplate;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-05 回归测试（plan 2026-08-15-2146-2 Phase 3）：ChatBI 生成路径 LLM 循环事务边界。
 *
 * <p>缺陷机制：{@code chatToDashboard}/{@code chatToScreen} 为 @BizMutation（GraphQL 事务装饰器
 * REQUIRED 事务），修复前整个 tool-calling 循环（单轮 LLM 远程调用可达数十秒 × maxIterations 轮）
 * 全程运行在事务内——并发请求可耗尽连接池。</p>
 *
 * <p><b>修复形态（裁定落档 ai-design.md §11）</b>：LLM 循环经 {@code runWithoutTransaction} 挂起
 * ambient 事务执行（远程调用不进事务，机制锚定 = 每次 LLM 调用时 {@code isTransactionOpened} 必须为
 * false）；循环内生成工具（DatavGenerateDashboard/ScreenExecutor）经 REQUIRED 独立短事务即时落库
 * （多表写原子性保持 + dashboardId 对后续轮次可读）；循环异常时补偿删除本轮全部生成物（失败不落库
 * 承诺保持——修复前由单事务整体回滚兑现，移出事务后由补偿删除兑现）。</p>
 *
 * <p>mutate-fail：若回退「循环运行在事务内」，LLM 调用时事务标志为 true → 首个断言确定性失败；
 * 若移除补偿删除，循环失败后生成物残留 → 补偿断言确定性失败。</p>
 */
public class TestNopDatavChatBiTransactionBoundary extends AbstractNopDatavTest {

    private static final String CALLER = "p105-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

    // ==================== P1-05：循环不持事务 + 生成物即时提交 ====================

    /**
     * 事务上下文内（镜像 @BizMutation 事务装饰器）调用 chatToDashboard：
     * (a) 每次远程 LLM 调用时线程事务注册必须为空（机制锚定：远程调用不进事务）；
     * (b) 生成工具的独立短事务即时提交——外层事务内已可读 dashboard 行（多轮可见性裁定）；
     * (c) 外层事务提交后生成物仍在（成功路径行为与修复前等价）。
     */
    @Test
    public void testChatToDashboardLoopHoldsNoTransactionAndPersistsImmediately() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        seedDataset("ds-p105-dash",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES order by AMOUNT desc",
                fieldsMeta("region", "amount"));

        String genArgs = JsonTool.stringify(dashSpec("P105 Dash",
                panel("Chart", "chart", "ds-p105-dash", fieldMapping("x", "region", "y", "amount"), 0)));
        TxnObservingChatService mockChat = new TxnObservingChatService(Arrays.asList(
                buildToolCallResponse("call-1", DatavGenerateDashboardExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("已创建草稿看板。")));

        NopDatavChatBiBizModel bizModel = newBizModel(mockChat);

        AtomicReference<String> dashboardIdRef = new AtomicReference<>();
        transactionTemplate.runInTransaction(txn -> {
            assertTrue(transactionTemplate.isTransactionOpened(null),
                    "precondition: ambient transaction open (mirrors @BizMutation decorator)");
            ChatBiResult result = bizModel.chatToDashboard("建一个销售额看板", newContext(CALLER));
            dashboardIdRef.set(result.getCreatedEntityId());
            assertNotNull(dashboardIdRef.get(), "createdEntityId set");
            // (b) 生成工具短事务已即时提交：外层事务内即已可读（多轮工具间 dashboardId 可见性）
            assertNotNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardIdRef.get()),
                    "generated dashboard visible inside outer transaction (short-txn immediate commit)");
            return null;
        });

        // (a) 机制锚定：所有远程 LLM 调用发生在无事务状态（挂起生效）
        assertTrue(mockChat.callCount() >= 2, "two LLM rounds (tool call + final answer)");
        assertTrue(mockChat.txnOpenAtCallStream().noneMatch(Boolean::booleanValue),
                "LLM remote calls must NOT run inside a database transaction (P1-05 mechanism anchor), flags="
                        + mockChat.txnOpenAtCallStream().collect(Collectors.toList()));

        // (c) 外层事务提交后生成物仍在（成功路径结果不变）
        assertNotNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardIdRef.get()),
                "generated dashboard persists after outer transaction commit");
    }

    // ==================== P1-05：循环失败补偿删除（失败不落库承诺） ====================

    /**
     * 循环第一轮生成成功、第二轮 LLM 失败：异常上抛 + 本轮生成物被补偿删除
     * （Dashboard/Panel/DatasetRef 全清，无半成品残留——修复前由单事务整体回滚兑现的
     * 「失败不落库」承诺，移出事务后由补偿删除兑现）。
     */
    @Test
    public void testChatToDashboardLoopFailureCompensatesGeneratedArtifacts() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        seedDataset("ds-p105-fail",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES",
                fieldsMeta("region", "amount"));

        String genArgs = JsonTool.stringify(dashSpec("P105 Fail Dash",
                panel("Chart", "chart", "ds-p105-fail", fieldMapping("x", "region", "y", "amount"), 0)));
        // round 1: 生成成功；round 2: LLM 故障（异常上抛）
        TxnObservingChatService mockChat = new TxnObservingChatService(Arrays.asList(
                buildToolCallResponse("call-f1", DatavGenerateDashboardExecutor.TOOL_NAME, genArgs),
                LLM_FAILURE_SENTINEL));

        NopDatavChatBiBizModel bizModel = newBizModel(mockChat);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> bizModel.chatToDashboard("生成后失败的看板", newContext(CALLER)));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("simulated LLM failure"),
                "original loop failure propagates (no swallow), got: " + ex.getMessage());

        // 补偿删除：无半成品残留
        assertEquals(0, daoProvider.daoFor(NopDatavDashboard.class).findAll().size(),
                "generated dashboard compensated (no-partial-artifact contract)");
        assertEquals(0, daoProvider.daoFor(NopDatavPanel.class).findAll().size(),
                "generated panels compensated");
        assertEquals(0, daoProvider.daoFor(NopDatavDatasetRef.class).findAll().size(),
                "generated datasetRefs compensated");
    }

    /**
     * 大屏路径对称：循环失败后 Screen/ScreenWidget 补偿删除。
     */
    @Test
    public void testChatToScreenLoopFailureCompensatesGeneratedArtifacts() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        seedDataset("ds-p105-screen",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES",
                fieldsMeta("region", "amount"));

        String genArgs = JsonTool.stringify(screenSpec("P105 Fail Screen", 1920, 1080, Arrays.asList(
                widget("chart", "ds-p105-screen", fieldMapping("x", "region", "y", "amount"), 0, 0, 800, 400, 0),
                widget("decorative-border", null, null, 0, 0, 1920, 80, 0))));
        TxnObservingChatService mockChat = new TxnObservingChatService(Arrays.asList(
                buildToolCallResponse("call-s1", DatavGenerateScreenExecutor.TOOL_NAME, genArgs),
                LLM_FAILURE_SENTINEL));

        NopDatavChatBiBizModel bizModel = newBizModel(mockChat);

        assertThrows(RuntimeException.class,
                () -> bizModel.chatToScreen("生成后失败的大屏", newContext(CALLER)));

        assertEquals(0, daoProvider.daoFor(NopDatavScreen.class).findAll().size(),
                "generated screen compensated (no-partial-artifact contract)");
        assertEquals(0, daoProvider.daoFor(NopDatavScreenWidget.class).findAll().size(),
                "generated widgets compensated");
    }

    /**
     * mutation 路径（ambient 事务存在）下的失败补偿：循环异常 → 补偿删除（REQUIRES_NEW 独立短事务，
     * 不随注定回滚的外层事务回滚——否则生成物复活违反失败不落库承诺）→ 异常继续上抛外层回滚。
     */
    @Test
    public void testChatToDashboardLoopFailureInsideTransactionCompensatesDespiteOuterRollback() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        seedDataset("ds-p105-fail-in-txn",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES",
                fieldsMeta("region", "amount"));

        String genArgs = JsonTool.stringify(dashSpec("P105 Fail In Txn Dash",
                panel("Chart", "chart", "ds-p105-fail-in-txn", fieldMapping("x", "region", "y", "amount"), 0)));
        TxnObservingChatService mockChat = new TxnObservingChatService(Arrays.asList(
                buildToolCallResponse("call-ft1", DatavGenerateDashboardExecutor.TOOL_NAME, genArgs),
                LLM_FAILURE_SENTINEL));

        NopDatavChatBiBizModel bizModel = newBizModel(mockChat);

        assertThrows(RuntimeException.class, () ->
                transactionTemplate.runInTransaction(txn -> {
                    bizModel.chatToDashboard("事务内生成后失败", newContext(CALLER));
                    return null;
                }));

        assertEquals(0, daoProvider.daoFor(NopDatavDashboard.class).findAll().size(),
                "compensation (REQUIRES_NEW) survives outer rollback: no dashboard resurrected");
        assertEquals(0, daoProvider.daoFor(NopDatavPanel.class).findAll().size(),
                "no panels resurrected");
        assertEquals(0, daoProvider.daoFor(NopDatavDatasetRef.class).findAll().size(),
                "no datasetRefs resurrected");
    }

    /**
     * 成功路径（无事务上下文直调，回归兼容形态）：生成物正常落库且不被补偿。
     */
    @Test
    public void testChatToDashboardSuccessWithoutTransactionKeepsArtifacts() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        seedDataset("ds-p105-ok",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES",
                fieldsMeta("region", "amount"));

        String genArgs = JsonTool.stringify(dashSpec("P105 OK Dash",
                panel("Chart", "chart", "ds-p105-ok", fieldMapping("x", "region", "y", "amount"), 0)));
        TxnObservingChatService mockChat = new TxnObservingChatService(Arrays.asList(
                buildToolCallResponse("call-ok", DatavGenerateDashboardExecutor.TOOL_NAME, genArgs),
                buildFinalAnswerResponse("完成。")));

        NopDatavChatBiBizModel bizModel = newBizModel(mockChat);
        ChatBiResult result = bizModel.chatToDashboard("建一个看板", newContext(CALLER));

        assertNotNull(result.getCreatedEntityId());
        NopDatavDashboard dash = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(result.getCreatedEntityId());
        assertNotNull(dash, "success path artifact persists (not compensated)");
        assertEquals("P105 OK Dash", dash.getDashboardName());
    }

    // ==================== Helpers ====================

    private NopDatavChatBiBizModel newBizModel(IChatService chatService) {
        NopDatavChatBiBizModel bizModel = new NopDatavChatBiBizModel();
        bizModel.setChatService(chatService);
        bizModel.setToolManager(buildToolManager());
        // P1-05 注入（生产由 IoC 容器注入；直调测试显式设置）
        bizModel.setTransactionTemplate(transactionTemplate);
        bizModel.setDaoProvider(daoProvider);
        bizModel.setOrmTemplate(ormTemplate);
        return bizModel;
    }

    private IToolManager buildToolManager() {
        DatavGenerateDashboardExecutor genDashExec = new DatavGenerateDashboardExecutor();
        genDashExec.setDaoProvider(daoProvider);
        genDashExec.setOrmTemplate(ormTemplate);
        genDashExec.setTransactionTemplate(transactionTemplate);

        DatavGenerateScreenExecutor genScreenExec = new DatavGenerateScreenExecutor();
        genScreenExec.setDaoProvider(daoProvider);
        genScreenExec.setOrmTemplate(ormTemplate);
        genScreenExec.setTransactionTemplate(transactionTemplate);

        DefaultToolExecutorProvider provider = new DefaultToolExecutorProvider();
        provider.setExecutors(Arrays.asList(genDashExec, genScreenExec));
        return new ToolManagerImpl(provider, Collections.<IToolCallInterceptor>emptyList());
    }

    private static ChatResponse buildToolCallResponse(String callId, String toolName, String argumentsJson) {
        ChatResponse response = new ChatResponse();
        @SuppressWarnings("unchecked")
        Map<String, Object> args = JsonTool.parseNonStrict(argumentsJson) instanceof Map
                ? (Map<String, Object>) JsonTool.parseNonStrict(argumentsJson)
                : new LinkedHashMap<>();
        response.addMessage(new ChatToolCallMessage(callId, toolName, args));
        return response;
    }

    private static ChatResponse buildFinalAnswerResponse(String answer) {
        ChatResponse response = new ChatResponse();
        response.addMessage(new ChatAssistantMessage(answer));
        return response;
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private static Map<String, Object> dashSpec(String dashName, Map<String, Object>... panels) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("dashboardName", dashName);
        spec.put("panels", Arrays.asList(panels));
        return spec;
    }

    private static Map<String, Object> panel(String title, String componentType, String datasetSid,
                                              Map<String, Object> fieldMapping, int sortOrder) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("componentType", componentType);
        if (datasetSid != null) {
            p.put("datasetSid", datasetSid);
        }
        if (fieldMapping != null) {
            p.put("fieldMapping", fieldMapping);
        }
        p.put("sortOrder", sortOrder);
        return p;
    }

    private static Map<String, Object> screenSpec(String screenName, int width, int height,
                                                   List<Map<String, Object>> widgets) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("screenName", screenName);
        spec.put("screenWidth", width);
        spec.put("screenHeight", height);
        spec.put("widgets", widgets);
        return spec;
    }

    private static Map<String, Object> widget(String componentType, String datasetSid,
                                               Map<String, Object> fieldMapping, int x, int y, int w, int h, int z) {
        Map<String, Object> wid = new LinkedHashMap<>();
        wid.put("componentType", componentType);
        if (datasetSid != null) {
            wid.put("datasetSid", datasetSid);
        }
        if (fieldMapping != null) {
            wid.put("fieldMapping", fieldMapping);
        }
        wid.put("x", x);
        wid.put("y", y);
        wid.put("w", w);
        wid.put("h", h);
        wid.put("z", z);
        return wid;
    }

    private static Map<String, Object> fieldMapping(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String fieldsMeta(String... names) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (String n : names) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", n);
            f.put("type", "string");
            fields.add(f);
        }
        return JsonTool.stringify(Collections.singletonMap("fields", fields));
    }

    private void seedDataset(String sid, String dsText, String dsMeta) {
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(sid);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText(dsText);
        ds.setDsMeta(dsMeta != null ? dsMeta : "{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(System.currentTimeMillis()));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);
    }

    private void createSalesTable() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    // ==================== Mock IChatService（事务观察 + 故障注入） ====================

    /**
     * 每次 {@link #call} 记录调用时线程事务注册状态（P1-05 机制锚定 seam）；
     * {@link #LLM_FAILURE_SENTINEL} 哨兵响应模拟 LLM 故障（异常上抛）。
     */
    private class TxnObservingChatService implements IChatService {
        private final List<ChatResponse> responses;
        private final List<Boolean> txnOpenFlags = Collections.synchronizedList(new ArrayList<>());
        private int index = 0;

        TxnObservingChatService(List<ChatResponse> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return java.util.concurrent.CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            txnOpenFlags.add(transactionTemplate.isTransactionOpened(null));
            int idx = index++;
            ChatResponse next = idx < responses.size() ? responses.get(idx) : buildFinalAnswerResponse("");
            if (next == LLM_FAILURE_SENTINEL) {
                throw new io.nop.api.core.exceptions.NopException(
                        io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_AI_NOT_AVAILABLE)
                        .param("question", "simulated LLM failure after artifact creation");
            }
            return next;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("callStream not supported in mock");
        }

        int callCount() {
            return txnOpenFlags.size();
        }

        java.util.stream.Stream<Boolean> txnOpenAtCallStream() {
            synchronized (txnOpenFlags) {
                return new ArrayList<>(txnOpenFlags).stream();
            }
        }
    }

    /** LLM 故障哨兵（identity 比较）。 */
    private static final ChatResponse LLM_FAILURE_SENTINEL = new ChatResponse();
}
