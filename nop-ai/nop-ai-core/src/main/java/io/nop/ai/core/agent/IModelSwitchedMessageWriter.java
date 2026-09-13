package io.nop.ai.core.agent;

/**
 * Extension point for persisting {@code model-switched} audit messages
 * (design {@code nop-ai-agent-usage-and-billing.md} §3.5 / plan 205 L2-21).
 *
 * <p>The ReAct loop calls {@link #writeModelSwitched} after
 * {@code IModelRouter.route()} returns, when the routed model differs from the
 * previous iteration's model. The message is an <b>audit record</b> — it is
 * persisted to the {@code nop_ai_session_message} table (role=80) and never
 * injected into the LLM reasoning context.
 *
 * <p>The shipped default is NoOpModelSwitchedMessageWriter (explicit
 * pass-through — discards the event). Functional implementations:
 * DbModelSwitchedMessageWriter (raw JDBC, embedded 部署，已弃用) 与
 * OrmModelSwitchedMessageWriter（nop-ai-service，ORM 管道，生产推荐——审计 AI-2 修复）。
 * SPI 位于 nop-ai-core 以便 service 层实现（nop-ai-agent 不可依赖 nop-ai-dao）。
 *
 * <p>This mirrors the {@code IUsageRecorder} / {@code NoOpUsageRecorder} /
 * {@code DbUsageRecorder} extension-point pattern.
 */
public interface IModelSwitchedMessageWriter {

    /**
     * Persist a model-switched audit message (role=80) to
     * {@code nop_ai_session_message}.
     *
     * @param sessionId     the session id; must not be null (the caller is
     *                      responsible for ensuring a non-null session before
     *                      calling — anonymous executions produce no audit
     *                      message)
     * @param fromModel     the previous model key ({@code provider:model}
     *                      composite key); never null
     * @param toModel       the current model key ({@code provider:model}
     *                      composite key); never null
     * @param routingReason the routing reason from {@code RoutingResult}; may
     *                      be null (e.g. {@code PassThroughModelRouter} returns
     *                      a non-null reason, but functional routers may not)
     * @param complexity    the complexity grade from {@code RoutingResult};
     *                      may be null (graceful degradation)
     * @param seq           the per-execution monotonically increasing sequence
     *                      number for this session's messages; managed by the
     *                      caller (ReAct loop local counter)
     */
    void writeModelSwitched(String sessionId, String fromModel, String toModel,
                           String routingReason, String complexity, long seq);
}
