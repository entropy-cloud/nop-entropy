package io.nop.ai.agent.runtime;

/**
 * Lifecycle status of an {@link AgentActor}. The seven values and their legal
 * transitions match the Actor state machine defined in
 * {@code nop-ai-agent-actor-runtime-vision.md} §3.2.
 *
 * <h2>State-transition diagram (foundational slice)</h2>
 * <pre>
 *   CREATED ──→ READY ──→ RUNNING ←──→ IDLE
 *                            │           │
 *                       error │           │ error
 *                            ▼           ▼
 *                          FAILED       FAILED
 *                            │
 *                  (any state)
 *                            │
 *                            ▼
 *                         STOPPED
 * </pre>
 *
 * <h3>Core transitions (implemented by the foundational Actor Runtime slice)</h3>
 * <ul>
 *   <li>{@code CREATED → READY}: {@code createActor} registers the actor in the
 *       registry and prepares it for execution.</li>
 *   <li>{@code READY → RUNNING}: the consumption loop's first poll attempt
 *       transitions the actor to active execution.</li>
 *   <li>{@code RUNNING ↔ IDLE}: an empty poll (timeout, no message) transitions
 *       to {@link #IDLE}; a subsequent non-empty poll transitions back to
 *       {@link #RUNNING}.</li>
 *   <li>{@code any → STOPPED}: {@code destroyActor} gracefully stops the actor
 *       (status set to {@link #STOPPED}, execution thread interrupted, actor
 *       removed from the registry).</li>
 *   <li>{@code any → FAILED}: an exception inside the consumption loop (e.g.
 *       {@code poll}/{@code ack} throwing) transitions the actor to
 *       {@link #FAILED} — the exception is logged, never silently swallowed.</li>
 * </ul>
 *
 * <h3>Reserved transitions (RecoveryManager successor — Phase 4)</h3>
 * <ul>
 *   <li>{@code FAILED → RECOVERING}: the {@link #RECOVERING} status is defined
 *       and recognised by the registry, but the foundational slice does not
 *       implement automatic recovery transitions. The {@code RecoveryManager}
 *       (a successor component) will drive {@code FAILED → RECOVERING →
 *       RUNNING/IDLE} transitions.</li>
 * </ul>
 *
 * <p>See plan 218 (L4-8) and vision §3.2.
 */
public enum AgentActorStatus {
    /**
     * Initial state: the actor instance has been created but not yet
     * registered in the {@link ActorRegistry} or prepared for execution.
     * Entered at construction time; transitions to {@link #READY}.
     */
    CREATED,

    /**
     * The actor has been registered in the {@link ActorRegistry} and is
     * ready to start its consumption loop. Entered by {@code createActor}
     * after successful registration; transitions to {@link #RUNNING} when
     * the consumption loop begins.
     */
    READY,

    /**
     * The consumption loop is actively running and the actor last observed
     * a message (or is performing its first poll). Entered from {@link #READY}
     * on the first poll and re-entered from {@link #IDLE} when a message
     * arrives.
     */
    RUNNING,

    /**
     * The consumption loop is running but the last poll returned no message
     * (empty/timeout). The actor is waiting for new messages. Entered from
     * {@link #RUNNING} on an empty poll; transitions back to {@link #RUNNING}
     * when a message arrives.
     */
    IDLE,

    /**
     * The consumption loop encountered an unrecoverable error (e.g. an
     * exception during {@code poll} or {@code ack}). The exception is logged
     * and the actor awaits recovery or manual intervention. Automatic
     * recovery ({@link #RECOVERING}) is a successor feature.
     */
    FAILED,

    /**
     * Reserved for the {@code RecoveryManager} successor (Phase 4). The
     * foundational slice defines and recognises this status but does not
     * implement automatic recovery transitions into or out of it.
     */
    RECOVERING,

    /**
     * Terminal state: the actor has been stopped via {@code destroyActor}
     * and removed from the {@link ActorRegistry}. No further transitions
     * are possible.
     */
    STOPPED
}
