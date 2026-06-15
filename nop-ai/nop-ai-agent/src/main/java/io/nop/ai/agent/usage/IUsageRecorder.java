package io.nop.ai.agent.usage;

/**
 * Layer 2 extension point for recording per-LLM-call usage data in the ReAct
 * loop (design {@code nop-ai-agent-usage-and-billing.md} §3.1 / §3.2).
 *
 * <p>Each time the ReAct loop receives a successful LLM response, it constructs
 * a {@link UsageRecord} from the response usage + routed options and invokes
 * {@link #record(UsageRecord)}. The shipped default is the pass-through
 * {@link NoOpUsageRecorder} (records nothing); production wiring registers a
 * functional recorder (e.g. {@code DbUsageRecorder} writing
 * {@code NopAiChatResponse} — L2-18) via
 * {@code DefaultAgentEngine.setUsageRecorder}.
 *
 * <p><b>Pass-through semantics of the default</b>: {@link NoOpUsageRecorder}
 * is an <i>explicit</i> pass-through, not a hidden gap. Usage tracking is not a
 * safety component — the system runs correctly with no recorder (it simply
 * does not persist per-call usage data). Replacing it with a functional
 * recorder adds observability/billing, not correctness.
 *
 * <p>This interface deliberately holds only {@link #record(UsageRecord)}.
 * Per-model aggregation queries ({@code summarizeByModel}) are a service-layer
 * concern and live in {@code NopAiChatResponseBizModel} (L2-20), not on this
 * runtime extension point — placing an unconsumed query method here would
 * violate the no-hollow-contract principle (plan 201 §设计裁定).
 */
public interface IUsageRecorder {

    /**
     * Record the usage data of a single LLM call. Implementations must be
     * non-blocking relative to the ReAct loop (the call is made inline at the
     * token accumulation point); slow sinks should offload internally.
     *
     * @param record non-null usage snapshot; the caller does not retain a
     *               reference after this call returns, so implementations may
     *               store it directly
     */
    void record(UsageRecord record);
}
