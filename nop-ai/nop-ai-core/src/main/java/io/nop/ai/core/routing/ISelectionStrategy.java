package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatRequest;

import java.util.List;
import java.util.Set;

/**
 * 模型类候选选择策略（plan 2026-08-15-0849-2，设计 §3.3，Q10 裁定：规则策略为同一接口的
 * 另一种实现，本接口即扩展点）。
 *
 * <p>输入 = 请求（{@link ChatRequest}，含 options）、候选集、健康状态视图（熔断/并发/冷却）、
 * 本轮已尝试候选集；输出 = 选中候选或 <b>null</b>（null = 当前无可用候选，调用方 fail-loud——
 * 不排队、不无限等待、不静默降级）。
 *
 * <p><b>语义契约</b>：
 * <ul>
 *   <li>熔断 OPEN / 并发饱和的候选应跳过（当前不可用）；跳过<b>不记失败</b>（并发超限是非失败
 *       事件，只影响本轮选择——设计 §3.3；策略实现不得在跳过时调用
 *       {@code ThresholdBreaker.recordFailure}——失败记账归编排层，见 router 契约）。</li>
 *   <li>本轮已尝试候选（同一游走执行内被选中过的候选）不得重复返回。</li>
 *   <li>策略不得修改候选集或健康状态（只读）。</li>
 * </ul>
 *
 * <p>健康视图缺失（null）为调用方编程错误——实现应显式 fail-fast
 * （{@code ERR_AI_AGENT_INVALID_ARG}），不得隐式假设"视为健康/视为不可用"。
 */
public interface ISelectionStrategy {

    /**
     * @param request    请求（非 null；options 可能为 null）
     * @param candidates 候选集（声明顺序；非 null）
     * @param health     候选健康状态视图（非 null；{@link IModelClassHealth#healthOf} 契约非 null）
     * @param attempted  本轮已尝试候选集（非 null；可为空集）
     * @return 选中候选；null = 无可用候选（调用方 fail-loud）
     */
    ModelClassCandidate select(ChatRequest request, List<ModelClassCandidate> candidates,
                               IModelClassHealth health, Set<ModelClassCandidate> attempted);
}
