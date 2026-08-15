package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.model.ModelClassModel;
import io.nop.ai.core.reliability.ICircuitBreaker;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模型类候选集游走器（plan 2026-08-15-0849-2，设计 §3.3，ROUTE-04）。
 *
 * <p><b>构造</b>：经 {@link #forRequest} 从请求解析——请求 model → 模型类（显式成员声明归属）→
 * 候选集展开。请求 model 未归属任何类 = <b>无路由组</b>（{@link #hasRoutingGroup()} == false，
 * 零回归：调用方沿用既有单 provider 行为，不进入游走）。
 *
 * <p><b>游走语义</b>：
 * <ul>
 *   <li>主动路径 {@link #selectNext()}：无失败事件（如并发饱和跳过）仅在<b>类内</b>游走；
 *       类内全饱和 → <b>全池饱和 fail-loud</b>（{@code ERR_AI_MODEL_CLASS_SATURATED}，
 *       不跨 provider 链——设计 §3.3 裁决：主动路径是预检语义，跨 provider 决策留给被动失败路径）。</li>
 *   <li>被动路径 {@link #selectNextAfterFailure()}：失败后重选（失败标记 = 编排调用本方法）；
 *       类内耗尽后经 {@code resolveFailoverChain(primaryProvider)} 扩展候选集
 *       （primary = 请求目标 provider，Phase 1 裁定）；扩展后仍无可用候选 → fail-loud。</li>
 * </ul>
 * <b>饱和语义（Phase 1/4 裁定）</b>：全池饱和覆盖两类情形——并发饱和与健康度饱和（全候选熔断
 * OPEN/不可用）——共用 {@code ERR_AI_MODEL_CLASS_SATURATED}（语义 = "当前无可用候选"）。
 *
 * <p><b>失败记账归属（Phase 4 裁定）</b>：被动路径候选失败后的熔断记账
 * （{@code ThresholdBreaker.recordFailure(modelKey)}）由<b>编排层</b>（W6/W7）调用——本类只提供
 * "失败标记入参（selectNextAfterFailure）+ 游走/扩展/饱和语义"，不在内部直接记熔断（纯选择机制；
 * "同一模型类内多账号连续失败记账跨账号累计"由编排按已尝试候选逐个 recordFailure 实现）。
 *
 * <p><b>并发记账</b>：健康视图经 {@link ConcurrencyRegistry} 读取进程内 per-账号 in-flight 计数
 * （acquire/release 挂钩编排归 W6/W7；本类只读）。</p>
 *
 * <p><b>状态语义</b>：per-execution 有状态游走器（对齐 {@code AccountChain} 模式——单线程 per-call
 * 独占，跨并发调用各建独立实例）；"本轮已尝试候选"由本类维护并喂给策略。
 */
public final class ModelClassRouter {

    private final ChatRequest request;
    private final ChatOptions options;
    private final ISelectionStrategy strategy;
    private final IModelClassHealth health;
    private final ModelClassModel modelClass;
    private final List<ModelClassCandidate> inClassCandidates;
    private final Set<ModelClassCandidate> attempted = new LinkedHashSet<>();
    private List<ModelClassCandidate> extendedCandidates;
    private boolean providerChainExtended = false;

    /**
     * @param request  请求（非 null；options 可能为 null）
     * @param options  请求目标选项（provider/model 的来源）；null = 视为空 options
     * @param strategy 选择策略；null = 默认策略（健康度 + 并发感知 + 声明序）
     * @param breaker  熔断器（复用既有 {@link ThresholdBreaker}）；null = 默认实例
     * @param registry 并发注册表；null = 新实例
     */
    public ModelClassRouter(ChatRequest request, ChatOptions options, ISelectionStrategy strategy,
                            ICircuitBreaker breaker, ConcurrencyRegistry registry) {
        this.request = request != null ? request : new ChatRequest();
        this.options = options != null ? options : new ChatOptions();
        this.strategy = strategy != null ? strategy : new DefaultSelectionStrategy();
        this.health = new CandidateHealthProvider(breaker, registry);
        this.modelClass = LlmConfigHelper.resolveModelClass(this.options.getModel());
        this.inClassCandidates = LlmConfigHelper.resolveModelClassCandidates(modelClass);
    }

    /**
     * 从请求创建游走器（等价于完整构造器）。
     */
    public static ModelClassRouter forRequest(ChatRequest request, ChatOptions options,
                                              ISelectionStrategy strategy, ICircuitBreaker breaker,
                                              ConcurrencyRegistry registry) {
        return new ModelClassRouter(request, options, strategy, breaker, registry);
    }

    /**
     * @return true 当请求 model 归属于某模型类（有路由组可游走）；false = 无路由组（零回归路径）
     */
    public boolean hasRoutingGroup() {
        return modelClass != null;
    }

    /**
     * @return 模型类 id（无路由组时为 null；诊断用）
     */
    public String getModelClassId() {
        return modelClass != null ? modelClass.getId() : null;
    }

    /**
     * @return 类内候选集（展开后的不可变视图；诊断用）
     */
    public List<ModelClassCandidate> getInClassCandidates() {
        return inClassCandidates;
    }

    /**
     * @return 本轮已尝试候选集（只读视图；诊断用）
     */
    public Set<ModelClassCandidate> getAttempted() {
        return attempted;
    }

    /**
     * 主动路径：在类内候选集游走选择下一个可用候选。
     *
     * @return 选中候选（已加入本轮已尝试集）
     * @throws NopException {@code ERR_AI_MODEL_CLASS_SATURATED} 当类内全池饱和
     *                      （并发饱和或健康度饱和；不跨 provider 链）
     */
    public ModelClassCandidate selectNext() {
        return doSelect(false);
    }

    /**
     * 被动路径（失败后重选，失败标记）：类内游走；类内耗尽后经 provider 链（
     * {@code resolveFailoverChain}，primary = 请求目标 provider）扩展候选集继续游走。
     *
     * @return 选中候选（已加入本轮已尝试集）
     * @throws NopException {@code ERR_AI_MODEL_CLASS_SATURATED} 当类内 + provider 链扩展后全池饱和
     */
    public ModelClassCandidate selectNextAfterFailure() {
        return doSelect(true);
    }

    private ModelClassCandidate doSelect(boolean allowProviderChain) {
        if (modelClass == null) {
            // 无路由组仍被调用 = 编排层误用：显式 fail-fast（不静默返回）。
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "ModelClassRouter has no routing group for model: " + options.getModel());
        }
        ModelClassCandidate candidate = strategy.select(request, currentPool(), health, attempted);
        if (candidate == null && allowProviderChain && !providerChainExtended) {
            extendWithProviderChain();
            candidate = strategy.select(request, currentPool(), health, attempted);
        }
        if (candidate == null) {
            // 全池饱和 fail-loud：并发饱和与健康度饱和共用此码（语义 = 无可用候选）。
            throw new NopException(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED)
                    .param(NopAiCoreErrors.ARG_MODEL_CLASS, modelClass.getId());
        }
        attempted.add(candidate);
        return candidate;
    }

    /**
     * 把选中候选下沉为 {@link ChatOptions}（复制原 options 并覆盖四字段）——W6/W7 编排经
     * {@code provider}/{@code model}/{@code accountKey}/{@code accountBaseUrl} 消费：
     * accountKey null = 主账号（凭证链语义）；accountBaseUrl null = provider 根 baseUrl。
     */
    public ChatOptions toChatOptions(ChatOptions original, ModelClassCandidate selected) {
        ChatOptions target = original != null ? original.copy() : new ChatOptions();
        target.setProvider(selected.getProvider());
        target.setModel(selected.getModel());
        target.setAccountKey(selected.getAccountKey());
        target.setAccountBaseUrl(selected.getAccountBaseUrl());
        return target;
    }

    private List<ModelClassCandidate> currentPool() {
        if (extendedCandidates == null || extendedCandidates.isEmpty()) {
            return inClassCandidates;
        }
        List<ModelClassCandidate> pool = new ArrayList<>(inClassCandidates.size() + extendedCandidates.size());
        pool.addAll(inClassCandidates);
        pool.addAll(extendedCandidates);
        return pool;
    }

    private void extendWithProviderChain() {
        providerChainExtended = true;
        // Phase 1 裁定：primary 键 = 请求目标 provider（经 LlmConfigHelper.getProvider 解析）。
        extendedCandidates = LlmConfigHelper.resolveProviderChainCandidates(LlmConfigHelper.getProvider(options));
    }
}
