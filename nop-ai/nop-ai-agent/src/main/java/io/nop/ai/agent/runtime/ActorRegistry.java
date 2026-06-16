package io.nop.ai.agent.runtime;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry of {@link AgentActor} instances, providing dual-index lookup by
 * both runtime instance identity ({@code actorId}) and persistent session
 * identity ({@code sessionId}).
 *
 * <p>Foundational-slice identity dimensions: {@code actorId} + {@code sessionId}
 * + {@code agentName}. Multi-tenant tenantId/userId isolation (vision §5.1)
 * is a successor that depends on normalising the tenant-identifier
 * propagation through {@code AgentExecutionContext} (the foundational
 * registry does not track tenantId/userId).
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent {@link #register} /
 * {@link #unregister} / query calls from multiple threads.
 * {@link InMemoryActorRegistry} achieves this via {@code ConcurrentHashMap}
 * dual indices.
 *
 * <p>See plan 218 (L4-8) and vision §4.2.
 */
public interface ActorRegistry {

    /**
     * Register an actor. If an actor with the same actorId is already
     * registered, it is replaced (defensive — {@code IActorRuntime}
     * implementations ensure fresh UUIDs for new actors). The session index
     * is updated to point to the new actor's id.
     *
     * @param actor the actor to register (must not be null)
     */
    void register(AgentActor actor);

    /**
     * Remove the actor with the given actorId from both indices. No-op if no
     * such actor is registered.
     *
     * @param actorId the UUID actor identity
     */
    void unregister(String actorId);

    /**
     * Look up an actor by its runtime instance identity.
     *
     * @param actorId the UUID actor identity
     * @return the actor, or empty if not registered
     */
    Optional<AgentActor> get(String actorId);

    /**
     * Look up an actor by its bound session identity.
     *
     * @param sessionId the persistent session identity
     * @return the actor bound to this session, or empty if none is registered
     */
    Optional<AgentActor> getBySession(String sessionId);

    /**
     * @return a snapshot collection of all registered actors. Never null;
     *         empty when the registry holds no actors.
     */
    Collection<AgentActor> getAll();
}
