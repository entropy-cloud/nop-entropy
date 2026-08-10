package io.nop.datav.service.entity;

import io.nop.ai.api.chat.IChatService;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.exceptions.NopException;
import io.nop.datav.service.chatbi.ChatBiResult;
import io.nop.datav.service.chatbi.ChatBiToolCallingLoop;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_MAX_ITERATIONS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_AI_NOT_AVAILABLE;

/**
 * ChatBI BizModel（D6-1）。经 GraphQL 暴露 {@code chatToQuery} action，接收自然语言问题 →
 * 经 {@link IChatService}（带 datav 工具）轻量自建 tool-calling 循环（裁定 A）→ 返回查询结果。
 *
 * <p>{@link IChatService} / {@link IToolManager} 经 {@code @Nullable} 注入（镜像 D5-1 {@code IJobScheduler}
 * 范式）。nop-ai 缺席时 action **显式抛 {@code ERR_DATAV_CHATBI_AI_NOT_AVAILABLE}**（非静默返回 null，
 * 见 Minimum Rules #24）。</p>
 *
 * <p>设计契约：{@code ai-dev/design/nop-datav/ai-design.md}。</p>
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
     * ChatBI 查询 action：自然语言 → tool-calling → 数据集查询 → 结构化结果。
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
        return loop.run(question, maxIterations);
    }
}
