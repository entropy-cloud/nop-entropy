package io.nop.datav.service.entity;

import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.NopDatavDashboardOwnerGuard;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiSessionManager;
import io.nop.datav.service.chatbi.ChatBiSystemPrompt;
import io.nop.datav.service.chatbi.ChatBiToolCallingLoop;
import io.nop.datav.service.chatbi.DatavGenerateDashboardExecutor;
import io.nop.datav.service.chatbi.DatavGenerateScreenExecutor;
import io.nop.datav.service.chatbi.ToolResultHandler;
import io.nop.datav.service.NopDatavOperatorResolver;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_HISTORY_MAX_CHARS;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_HISTORY_MAX_TURNS;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_MAX_ITERATIONS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_AI_NOT_AVAILABLE;

/**
 * ChatBI BizModel（D6-1 查询 + D6-1b 看板生成 + D6-2 大屏生成 + D6-1 follow-up 多轮会话）。
 *
 * <p>经 GraphQL 暴露 7 个 action：
 * <ul>
 *   <li>{@code chatToQuery}（{@code @BizQuery}）：NL → tool-calling → 数据集查询 → 结构化结果。
 *       可选 {@code sessionId} 参数（裁定 S3）：缺省单轮（行为与 D6-1 逐字节等价）；携带时进入多轮会话
 *       模式（读历史注入 → 执行 → 本轮问答落会话）。</li>
 *   <li>{@code chatToDashboard}（{@code @BizMutation @Auth}，D6-1b）与 {@code chatToScreen}
 *       （{@code @BizMutation @Auth}，D6-2）：生成路径，单轮，多轮化为显式 Non-Goal。</li>
 *   <li>多轮会话管理（裁定 S5，全部 owner 校验后触达）：{@code createChatSession}（{@code @BizMutation}）、
 *       {@code listChatSessions}（{@code @BizQuery}）、{@code getChatSessionHistory}（{@code @BizQuery}）、
 *       {@code deleteChatSession}（{@code @BizMutation}）。</li>
 * </ul>
 * </p>
 *
 * <p>{@link IChatService} / {@link IToolManager} 经 {@code @Nullable} 注入（镜像 D5-1 {@code IJobScheduler}
 * 范式）。nop-ai 缺席时 action **显式抛 {@code ERR_DATAV_CHATBI_AI_NOT_AVAILABLE}**（非静默返回 null，
 * 见 Minimum Rules #24）。{@link ChatBiSessionManager} 为本模块自有 bean（经真实持久层），
 * 由 IoC 容器保证注入。</p>
 *
 * <p>设计契约：{@code ai-dev/design/nop-datav/ai-design.md}（§1–7 D6-1，§8 D6-1b，§9 D6-2，§10 多轮会话）。</p>
 */
@BizModel("NopDatavChatBi")
public class NopDatavChatBiBizModel {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavChatBiBizModel.class);

    @Nullable
    private IChatService chatService;

    @Nullable
    private IToolManager toolManager;

    private ChatBiSessionManager sessionManager;

    /**
     * P1-05 修复（plan 2026-08-15-2146-2 Phase 3）：事务模板——生成路径的 LLM tool-calling 循环
     * 经 {@code runWithoutTransaction} 挂起 ambient 事务（远程 LLM 调用不进事务），循环结束后恢复。
     * 未注入（直调单测 {@code new} 构造）时循环按原方式执行（无挂起需求——本就无事务）。
     */
    @Nullable
    private ITransactionTemplate transactionTemplate;

    /**
     * P1-05：生成工具失败补偿（循环异常时删除本轮已生成实体）所需 DAO 提供者。未注入时补偿
     * 显式 ERROR 日志（不静默吞）。
     */
    @Nullable
    private IDaoProvider daoProvider;

    /**
     * P1-05：补偿删除在独立短事务 + 新 session 中执行（多子行删除原子性）。
     */
    @Nullable
    private IOrmTemplate ormTemplate;

    /**
     * 注入 {@link IChatService}（{@code @Nullable}——宿主未注册 nop-ai chat 实现时不注入）。
     * nop-ai 缺席时 action 显式失败。
     */
    @Inject
    public void setChatService(@Nullable IChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 注入 {@link IToolManager}（{@code @Nullable}——宿主未注册 nop-ai toolkit 时不注入）。
     * nop-ai 缺席时 action 显式失败。
     */
    @Inject
    public void setToolManager(@Nullable IToolManager toolManager) {
        this.toolManager = toolManager;
    }

    /**
     * 注入多轮会话管理器（本模块自有 bean，经真实持久层；裁定 S1–S4）。
     */
    @Inject
    public void setSessionManager(ChatBiSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * P1-05：注入事务模板（bean {@code nopTransactionTemplate}）——生成路径 LLM 循环挂起 ambient
     * 事务（远程调用不进事务）。未注入（直调单测）时无挂起（本就无事务），行为回归兼容。
     */
    @Inject
    public void setTransactionTemplate(@Nullable ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * P1-05：注入 DAO 提供者与 ORM 模板——循环失败补偿删除（本轮已生成 Dashboard/Panel/DatasetRef
     * 与 Screen/ScreenWidget）在独立短事务 + 新 session 中执行。未注入时补偿显式 ERROR（不静默吞）。
     */
    @Inject
    public void setDaoProvider(@Nullable IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(@Nullable IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    // ==================== P1-05：LLM 循环事务边界（远程调用不进事务） ====================

    /**
     * P1-05 修复核心：LLM tool-calling 循环在挂起 ambient 事务的状态下执行——
     * {@code chatToDashboard}/{@code chatToScreen} 为 @BizMutation（GraphQL 事务装饰器 REQUIRED 事务），
     * 修复前整个循环（单轮 LLM 远程调用可达数十秒 × maxIterations 轮 + 工具落库）全程持事务，
     * 并发请求可耗尽连接池。{@code runWithoutTransaction} 在循环期间注销线程事务注册（循环内
     * 工具写经各自短事务即时提交），循环结束恢复注册，外层事务正常 commit（空事务）。
     *
     * <p>无 ambient 事务（直调单测 / @BizQuery 路径）或模板未注入时原样执行。</p>
     */
    private <T> T runLoopOutsideTransaction(Supplier<T> loopBody) {
        if (transactionTemplate == null) {
            return loopBody.get();
        }
        return transactionTemplate.runWithoutTransaction(null, loopBody::get);
    }
    /** P1-05：补偿删除本轮生成的看板（Panel + DatasetRef + Dashboard），独立短事务 + 新 session。 */
    private void compensateGeneratedDashboards(List<String> dashboardIds) {
        for (String dashboardId : dashboardIds) {
            try {
                runCompensationInShortTransaction(() -> {
                    IEntityDao<NopDatavPanel> panelDao = daoProvider.daoFor(NopDatavPanel.class);
                    QueryBean pq = new QueryBean();
                    pq.addFilter(FilterBeans.eq("dashboardId", dashboardId));
                    for (NopDatavPanel panel : (List<NopDatavPanel>) panelDao.findAllByQuery(pq)) {
                        panelDao.deleteEntityDirectly(panel);
                    }
                    IEntityDao<NopDatavDatasetRef> refDao = daoProvider.daoFor(NopDatavDatasetRef.class);
                    QueryBean rq = new QueryBean();
                    rq.addFilter(FilterBeans.eq("dashboardId", dashboardId));
                    for (NopDatavDatasetRef ref : (List<NopDatavDatasetRef>) refDao.findAllByQuery(rq)) {
                        refDao.deleteEntityDirectly(ref);
                    }
                    IEntityDao<NopDatavDashboard> dashDao = daoProvider.daoFor(NopDatavDashboard.class);
                    NopDatavDashboard dash = dashDao.getEntityById(dashboardId);
                    if (dash != null) {
                        dashDao.deleteEntityDirectly(dash);
                    }
                });
                LOG.warn("nop.datav.chatbi.compensated-generated-dashboard:dashboardId={} "
                        + "(generation loop failed, no-partial-artifact contract)", dashboardId);
            } catch (Exception e) {
                LOG.error("nop.datav.chatbi.compensate-failed:dashboardId={} (manual cleanup required)",
                        dashboardId, e);
            }
        }
    }

    /** P1-05：补偿删除本轮生成的大屏（ScreenWidget + Screen），独立短事务 + 新 session。 */
    private void compensateGeneratedScreens(List<String> screenIds) {
        for (String screenId : screenIds) {
            try {
                runCompensationInShortTransaction(() -> {
                    IEntityDao<NopDatavScreenWidget> widgetDao = daoProvider.daoFor(NopDatavScreenWidget.class);
                    QueryBean wq = new QueryBean();
                    wq.addFilter(FilterBeans.eq("screenId", screenId));
                    for (NopDatavScreenWidget widget : (List<NopDatavScreenWidget>) widgetDao.findAllByQuery(wq)) {
                        widgetDao.deleteEntityDirectly(widget);
                    }
                    IEntityDao<NopDatavScreen> screenDao = daoProvider.daoFor(NopDatavScreen.class);
                    NopDatavScreen screen = screenDao.getEntityById(screenId);
                    if (screen != null) {
                        screenDao.deleteEntityDirectly(screen);
                    }
                });
                LOG.warn("nop.datav.chatbi.compensated-generated-screen:screenId={} "
                        + "(generation loop failed, no-partial-artifact contract)", screenId);
            } catch (Exception e) {
                LOG.error("nop.datav.chatbi.compensate-failed:screenId={} (manual cleanup required)",
                        screenId, e);
            }
        }
    }

    /**
     * P1-05：补偿体执行形态——独立短事务（REQUIRES_NEW：异常上抛路径下 ambient 事务已被
     * {@code runWithoutTransaction} 恢复注册且注定回滚，REQUIRED 加入会使补偿删除随之回滚——
     * 生成物复活，违反失败不落库承诺）+ 新 session。模板/ormTemplate 未注入（直调单测）时
     * 降级直接执行（仍逐 id try/catch，不静默吞）。
     */
    private void runCompensationInShortTransaction(Runnable body) {
        if (daoProvider == null) {
            LOG.error("nop.datav.chatbi.compensate-unavailable:daoProvider not injected, cannot compensate");
            return;
        }
        if (transactionTemplate == null || ormTemplate == null) {
            body.run();
            return;
        }
        transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRES_NEW,
                txn -> ormTemplate.runInNewSession(session -> {
                    body.run();
                    return null;
                }));
    }

    /**
     * ChatBI 查询 action：自然语言 → tool-calling → 数据集查询 → 结构化结果（D6-1 + 多轮会话裁定 S3）。
     *
     * <p>{@code sessionId} 缺省（null/空）时单轮行为与 D6-1 完全一致（不触碰会话表，LLM 请求仅
     * system prompt + 本轮问题）。携带时进入会话模式：会话必须存在且属当前操作者（不存在/已删抛
     * {@code ERR_DATAV_CHATBI_SESSION_NOT_FOUND}，非本人抛 {@code ERR_DATAV_CHATBI_NOT_SESSION_OWNER}，
     * 无静默降级单轮）→ 按 S2 构造历史注入上下文（双上界取小、最老优先丢弃）→ 执行循环 →
     * 本轮 user 消息 + assistant 结果落会话。失败轮次（循环抛异常）不落库。</p>
     *
     * @param question  自然语言问题（必填）
     * @param sessionId 可选会话标识（null = 单轮；引用已有会话续接多轮）
     * @param context   服务上下文（会话模式解析 operator/归属）
     * @return ChatBI 结果（answer + columns + rows + iterations；会话模式回显 sessionId）
     */
    @BizQuery
    @Auth(permissions = "NopDatavChatBi:chatToQuery")
    public ChatBiResult chatToQuery(@Name("question") String question,
                                     @Name("sessionId") @Nullable String sessionId,
                                     IServiceContext context) {
        if (chatService == null || toolManager == null) {
            throw new NopException(ERR_DATAV_CHATBI_AI_NOT_AVAILABLE)
                    .param("question", question);
        }

        int maxIterations = CFG_DATAV_CHATBI_MAX_ITERATIONS.get();

        // P1-03 裁定 D4：身份（operator + admin）随循环传递到 tool executor，做数据集可见性判定
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        boolean admin = isAdminContext(context);

        // 单轮路径（缺省参数）：与 D6-1 行为逐字节等价（回归保护）
        if (sessionId == null || sessionId.isEmpty()) {
            ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
            return loop.run(question, ChatBiSystemPrompt.buildSystemPrompt(), operator, admin,
                    maxIterations, ChatBiToolCallingLoop.ChatBiQueryResultHandlers.QUERY_HANDLER, null);
        }

        // 多轮会话路径（裁定 S3/S4）：归属校验 → 历史注入 → 执行 → 落库
        NopDatavChatSession session = sessionManager.requireSession(sessionId, operator);
        List<NopDatavChatMessage> history = sessionManager.loadMessages(sessionId);
        List<ChatMessage> historyContext = sessionManager.buildHistoryContext(history,
                CFG_DATAV_CHATBI_HISTORY_MAX_TURNS.get(), CFG_DATAV_CHATBI_HISTORY_MAX_CHARS.get());

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
        ChatBiResult result = loop.run(question, ChatBiSystemPrompt.buildSystemPrompt(), operator, admin,
                maxIterations, ChatBiToolCallingLoop.ChatBiQueryResultHandlers.QUERY_HANDLER, historyContext);
        result.setSessionId(sessionId);
        sessionManager.appendTurn(session, question, result, operator);
        return result;
    }

    /**
     * 当前调用者是否 admin 角色（P1-03 裁定 D4）。优先 {@code IServiceContext.getUserContext()}，
     * 缺席时回退线程级 {@code IUserContext.get()}（生产链路两者同源；直调单测场景由测试显式设置）。
     */
    private static boolean isAdminContext(IServiceContext context) {
        IUserContext userContext = context != null && context.getUserContext() != null
                ? context.getUserContext()
                : IUserContext.get();
        return userContext != null && userContext.getRoles() != null
                && userContext.getRoles().contains(NopDatavDashboardOwnerGuard.ROLE_ADMIN);
    }

    // ==================== 多轮会话管理 action（裁定 S5） ====================

    /**
     * 创建空会话（裁定 S3：显式 create 语义；拒绝客户端生成 id 与首轮无参自动建会话）。
     *
     * @param sessionTitle 可选会话标题（空则首轮提问后以问题截断回填，裁定 S1）
     * @param context      服务上下文（归属 userName）
     * @return 新建会话（含 sessionId，供后续 chatToQuery 续接）
     */
    @BizMutation
    @Auth(permissions = "NopDatavChatBi:createChatSession")
    public NopDatavChatSession createChatSession(@Name("sessionTitle") @Nullable String sessionTitle,
                                                  IServiceContext context) {
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        return sessionManager.createSession(sessionTitle, operator);
    }

    /**
     * 列出本人的会话（按 updateTime 降序；仅含本人会话，裁定 S4 归属隔离）。
     */
    @BizQuery
    @Auth(permissions = "NopDatavChatBi:listChatSessions")
    public List<NopDatavChatSession> listChatSessions(IServiceContext context) {
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        return sessionManager.listSessions(operator);
    }

    /**
     * 取回某会话的历史消息（按 seq 升序，含 RESULT_JSON 留存内容，裁定 S4 数据留存）。
     * 不存在/已删/非本人显式抛错（与 chatToQuery 续接同一组错误码）。
     */
    @BizQuery
    @Auth(permissions = "NopDatavChatBi:getChatSessionHistory")
    public List<NopDatavChatMessage> getChatSessionHistory(@Name("sessionId") String sessionId,
                                                            IServiceContext context) {
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        sessionManager.requireSession(sessionId, operator);
        return sessionManager.loadMessages(sessionId);
    }

    /**
     * 删除会话及其全部消息（物理删除，裁定 S4 删除语义）。删除后续接/查历史均显式报错。
     */
    @BizMutation
    @Auth(permissions = "NopDatavChatBi:deleteChatSession")
    public void deleteChatSession(@Name("sessionId") String sessionId, IServiceContext context) {
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        sessionManager.deleteSession(sessionId, operator);
    }

    /**
     * ChatBI 看板生成 action（D6-1b）：自然语言描述 → tool-calling → 草稿看板生成 → dashboardId。
     *
     * <p>写操作（创建 Dashboard/Panel/DatasetRef 实体），故用 {@code @BizMutation} 非 {@code @BizQuery}，
     * 镜像 {@code publishDashboard(@BizMutation)} 约定。经泛化循环（裁定 L）+ 看板生成专用 system prompt +
     * operator 传递（裁定 G）+ 看板生成结果提取 handler。</p>
     *
     * <p><b>P1-05 事务边界（plan 2026-08-15-2146-2 Phase 3）</b>：LLM tool-calling 循环（远程调用）
     * 经 {@link #runLoopOutsideTransaction} 挂起 ambient 事务执行（远程调用不进事务）；循环内生成工具
     * 经独立短事务即时落库并向 LLM 返回 dashboardId；循环异常时补偿删除本轮全部生成物（失败不落库
     * 承诺保持，见 {@link #compensateGeneratedDashboards}）。</p>
     *
     * <p>nop-ai 缺席时显式抛 {@code ERR_DATAV_CHATBI_AI_NOT_AVAILABLE}（复用 D6-1 @Nullable 范式）。
     * 生成的看板为 DRAFT（不自动发布，裁定 H），用户须手动 {@code publishDashboard} 审阅发布。</p>
     *
     * @param description 自然语言看板描述（必填，如"建一个各地区销售额分析的看板"）
     * @param context     服务上下文（解析 operator，填充 createdBy；镜像 publishDashboard 约定）
     * @return ChatBI 结果（answer + createdEntityId=dashboardId + iterations）
     */
    @BizMutation
    @Auth(permissions = "NopDatavChatBi:chatToDashboard")
    public ChatBiResult chatToDashboard(@Name("description") String description, IServiceContext context) {
        if (chatService == null || toolManager == null) {
            throw new NopException(ERR_DATAV_CHATBI_AI_NOT_AVAILABLE)
                    .param("question", description);
        }

        // 裁定 G：从 IServiceContext 解析 operator，传入泛化循环 → 生成 executor 读取 → 手动填充 createdBy
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        boolean admin = isAdminContext(context);

        int maxIterations = CFG_DATAV_CHATBI_MAX_ITERATIONS.get();

        // P1-05：记录本轮生成的 dashboardId（补偿删除清单）；循环挂起事务执行，异常时补偿后原样上抛
        List<String> createdDashboardIds = new ArrayList<>();
        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
        try {
            return runLoopOutsideTransaction(() -> loop.run(description,
                    ChatBiSystemPrompt.buildDashboardSystemPrompt(), operator, admin,
                    maxIterations, newDashboardResultHandler(createdDashboardIds)));
        } catch (RuntimeException e) {
            compensateGeneratedDashboards(createdDashboardIds);
            throw e;
        }
    }

    /**
     * 看板生成路径结果提取 handler 工厂（裁定 K + 裁定 L 泛化点 2 + P1-05 补偿清单）。
     *
     * <p>当 {@code datav-generate-dashboard} 成功执行时，从返回 JSON 解析 dashboardId 累加进
     * {@link ChatBiResult#setCreatedEntityId}，并记录进 {@code createdDashboardIdsSink}（循环失败的
     * 补偿删除清单）。sink 为 null 时退化为纯结果提取（回归兼容）。</p>
     */
    private static ToolResultHandler newDashboardResultHandler(List<String> createdDashboardIdsSink) {
        return (toolName, result, content, accumulator) -> {
            if (!DatavGenerateDashboardExecutor.TOOL_NAME.equals(toolName)) {
                return;
            }
            if (!"success".equals(result.getStatus()) || result.getError() != null || content == null) {
                return;
            }
            try {
                Object parsed = JsonTool.parseNonStrict(content);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Object dashboardId = ((Map<String, Object>) parsed).get("dashboardId");
                    if (dashboardId != null) {
                        accumulator.setCreatedEntityId(dashboardId.toString());
                        if (createdDashboardIdsSink != null) {
                            createdDashboardIdsSink.add(dashboardId.toString());
                        }
                    }
                }
            } catch (Exception ignore) {
                // 解析失败不影响循环（与 query handler 容忍一致）
            }
        };
    }

    /**
     * ChatBI 大屏生成 action（D6-2）：自然语言描述 → tool-calling → 草稿大屏生成 → screenId。
     *
     * <p>写操作（创建 Screen/ScreenWidget 实体），故用 {@code @BizMutation}（镜像 {@code chatToDashboard} 约定）。
     * 经泛化循环（裁定 L）+ 大屏生成专用 system prompt + operator 传递（裁定 G）+ 大屏生成结果提取 handler。</p>
     *
     * <p><b>P1-05 事务边界（plan 2026-08-15-2146-2 Phase 3）</b>：与 {@code chatToDashboard} 对称——
     * LLM 循环挂起 ambient 事务执行，循环内生成工具独立短事务落库，循环异常补偿删除（见
     * {@link #compensateGeneratedScreens}）。</p>
     *
     * <p>nop-ai 缺席时显式抛 {@code ERR_DATAV_CHATBI_AI_NOT_AVAILABLE}（复用 D6-1 @Nullable 范式）。
     * 生成的大屏为 DRAFT（不自动发布，沿用裁定 H），用户须手动 {@code publishScreen} 审阅发布。</p>
     *
     * @param description 自然语言大屏描述（必填，如"建一个经营 KPI 大屏，1920x1080"）
     * @param context     服务上下文（解析 operator，填充 createdBy；镜像 publishScreen 约定）
     * @return ChatBI 结果（answer + createdEntityId=screenId + iterations）
     */
    @BizMutation
    @Auth(permissions = "NopDatavChatBi:chatToScreen")
    public ChatBiResult chatToScreen(@Name("description") String description, IServiceContext context) {
        if (chatService == null || toolManager == null) {
            throw new NopException(ERR_DATAV_CHATBI_AI_NOT_AVAILABLE)
                    .param("question", description);
        }

        // 裁定 G：从 IServiceContext 解析 operator，传入泛化循环 → 生成 executor 读取 → 手动填充 createdBy
        String operator = NopDatavOperatorResolver.resolveOperator(context);
        boolean admin = isAdminContext(context);

        int maxIterations = CFG_DATAV_CHATBI_MAX_ITERATIONS.get();

        // P1-05：记录本轮生成的 screenId（补偿删除清单）；循环挂起事务执行，异常时补偿后原样上抛
        List<String> createdScreenIds = new ArrayList<>();
        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
        try {
            return runLoopOutsideTransaction(() -> loop.run(description,
                    ChatBiSystemPrompt.buildScreenSystemPrompt(), operator, admin,
                    maxIterations, newScreenResultHandler(createdScreenIds)));
        } catch (RuntimeException e) {
            compensateGeneratedScreens(createdScreenIds);
            throw e;
        }
    }

    /**
     * 大屏生成路径结果提取 handler 工厂（D6-2，裁定 K + 裁定 L 泛化点 2 + P1-05 补偿清单）。
     *
     * <p>当 {@code datav-generate-screen} 成功执行时，从返回 JSON 解析 screenId 累加进
     * {@link ChatBiResult#setCreatedEntityId}，并记录进 {@code createdScreenIdsSink}（循环失败的
     * 补偿删除清单）。sink 为 null 时退化为纯结果提取（回归兼容）。</p>
     */
    private static ToolResultHandler newScreenResultHandler(List<String> createdScreenIdsSink) {
        return (toolName, result, content, accumulator) -> {
            if (!DatavGenerateScreenExecutor.TOOL_NAME.equals(toolName)) {
                return;
            }
            if (!"success".equals(result.getStatus()) || result.getError() != null || content == null) {
                return;
            }
            try {
                Object parsed = JsonTool.parseNonStrict(content);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Object screenId = ((Map<String, Object>) parsed).get("screenId");
                    if (screenId != null) {
                        accumulator.setCreatedEntityId(screenId.toString());
                        if (createdScreenIdsSink != null) {
                            createdScreenIdsSink.add(screenId.toString());
                        }
                    }
                }
            } catch (Exception ignore) {
                // 解析失败不影响循环（与 query/dashboard handler 容忍一致）
            }
        };
    }
}
