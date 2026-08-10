package io.nop.datav.service.entity;

import io.nop.ai.api.chat.IChatService;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiSystemPrompt;
import io.nop.datav.service.chatbi.ChatBiToolCallingLoop;
import io.nop.datav.service.chatbi.DatavGenerateDashboardExecutor;
import io.nop.datav.service.chatbi.DatavGenerateScreenExecutor;
import io.nop.datav.service.chatbi.ToolResultHandler;
import io.nop.datav.service.NopDatavOperatorResolver;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.Map;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_MAX_ITERATIONS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_AI_NOT_AVAILABLE;

/**
 * ChatBI BizModel（D6-1 查询 + D6-1b 看板生成 + D6-2 大屏生成）。
 *
 * <p>经 GraphQL 暴露三个 action：
 * <ul>
 *   <li>{@code chatToQuery}（{@code @BizQuery}）：NL → tool-calling → 数据集查询 → 结构化结果。</li>
 *   <li>{@code chatToDashboard}（{@code @BizMutation @Auth}，D6-1b）：NL → tool-calling → 看板生成 →
 *       草稿看板 dashboardId。写操作（创建实体），故用 {@code @BizMutation} 非 {@code @BizQuery}，
 *       镜像 {@code publishDashboard(@BizMutation)} 约定。</li>
 *   <li>{@code chatToScreen}（{@code @BizMutation @Auth}，D6-2）：NL → tool-calling → 大屏生成 →
 *       草稿大屏 screenId。写操作（创建 Screen/ScreenWidget 实体），镜像 {@code chatToDashboard} 约定。</li>
 * </ul>
 * </p>
 *
 * <p>{@link IChatService} / {@link IToolManager} 经 {@code @Nullable} 注入（镜像 D5-1 {@code IJobScheduler}
 * 范式）。nop-ai 缺席时 action **显式抛 {@code ERR_DATAV_CHATBI_AI_NOT_AVAILABLE}**（非静默返回 null，
 * 见 Minimum Rules #24）。</p>
 *
 * <p>设计契约：{@code ai-dev/design/nop-datav/ai-design.md}（§1–7 D6-1，§8 D6-1b，§9 D6-2）。</p>
 */
@BizModel("NopDatavChatBi")
public class NopDatavChatBiBizModel {

    @Nullable
    private IChatService chatService;

    @Nullable
    private IToolManager toolManager;

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
     * ChatBI 查询 action：自然语言 → tool-calling → 数据集查询 → 结构化结果（D6-1）。
     *
     * <p>迁移到泛化循环（裁定 L）后行为不变：传入查询专用 system prompt + null operator（查询不写实体）+
     * 查询结果提取 handler。回归测试保护既有查询行为。</p>
     *
     * @param question 自然语言问题（必填）
     * @return ChatBI 结果（answer + columns + rows + iterations）
     */
    @BizQuery
    @Auth(permissions = "NopDatavChatBi:chatToQuery")
    public ChatBiResult chatToQuery(@Name("question") String question) {
        if (chatService == null || toolManager == null) {
            throw new NopException(ERR_DATAV_CHATBI_AI_NOT_AVAILABLE)
                    .param("question", question);
        }

        int maxIterations = CFG_DATAV_CHATBI_MAX_ITERATIONS.get();

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
        return loop.run(question, ChatBiSystemPrompt.buildSystemPrompt(), null,
                maxIterations, ChatBiToolCallingLoop.ChatBiQueryResultHandlers.QUERY_HANDLER);
    }

    /**
     * ChatBI 看板生成 action（D6-1b）：自然语言描述 → tool-calling → 草稿看板生成 → dashboardId。
     *
     * <p>写操作（创建 Dashboard/Panel/DatasetRef 实体），故用 {@code @BizMutation} 非 {@code @BizQuery}，
     * 镜像 {@code publishDashboard(@BizMutation)} 约定。经泛化循环（裁定 L）+ 看板生成专用 system prompt +
     * operator 传递（裁定 G）+ 看板生成结果提取 handler。</p>
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

        int maxIterations = CFG_DATAV_CHATBI_MAX_ITERATIONS.get();

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
        return loop.run(description, ChatBiSystemPrompt.buildDashboardSystemPrompt(), operator,
                maxIterations, DASHBOARD_RESULT_HANDLER);
    }

    /**
     * 看板生成路径结果提取 handler（裁定 K + 裁定 L 泛化点 2）。
     *
     * <p>当 {@code datav-generate-dashboard} 成功执行时，从返回 JSON 解析 dashboardId 累加进
     * {@link ChatBiResult#setCreatedEntityId}。</p>
     */
    private static final ToolResultHandler DASHBOARD_RESULT_HANDLER = (toolName, result, content, accumulator) -> {
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
                }
            }
        } catch (Exception ignore) {
            // 解析失败不影响循环（与 query handler 容忍一致）
        }
    };

    /**
     * ChatBI 大屏生成 action（D6-2）：自然语言描述 → tool-calling → 草稿大屏生成 → screenId。
     *
     * <p>写操作（创建 Screen/ScreenWidget 实体），故用 {@code @BizMutation}（镜像 {@code chatToDashboard} 约定）。
     * 经泛化循环（裁定 L）+ 大屏生成专用 system prompt + operator 传递（裁定 G）+ 大屏生成结果提取 handler。</p>
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

        int maxIterations = CFG_DATAV_CHATBI_MAX_ITERATIONS.get();

        ChatBiToolCallingLoop loop = new ChatBiToolCallingLoop(chatService, toolManager);
        return loop.run(description, ChatBiSystemPrompt.buildScreenSystemPrompt(), operator,
                maxIterations, SCREEN_RESULT_HANDLER);
    }

    /**
     * 大屏生成路径结果提取 handler（D6-2，裁定 K + 裁定 L 泛化点 2）。
     *
     * <p>当 {@code datav-generate-screen} 成功执行时，从返回 JSON 解析 screenId 累加进
     * {@link ChatBiResult#setCreatedEntityId}。</p>
     */
    private static final ToolResultHandler SCREEN_RESULT_HANDLER = (toolName, result, content, accumulator) -> {
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
                }
            }
        } catch (Exception ignore) {
            // 解析失败不影响循环（与 query/dashboard handler 容忍一致）
        }
    };
}
