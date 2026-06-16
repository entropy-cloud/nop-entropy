package io.nop.ai.agent.runtime;

import java.util.Collection;
import java.util.Optional;

/**
 * Opt-in Actor container that manages {@link AgentActor} instances. When
 * enabled ({@link #isEnabled()} returns {@code true}), the runtime creates
 * an Actor for each agent execution session, binds it to the session's
 * deferred-ack {@link io.nop.ai.agent.message.IMailbox mailbox}, and runs an
 * observation-only consumption loop on a dedicated single thread. When
 * disabled (the shipped {@link NoOpActorRuntime} default), the engine skips
 * the Actor path entirely — no exceptions, no silent side effects.
 *
 * <h2>Opt-in contract</h2>
 * The engine consults {@link #isEnabled()} before calling any lifecycle
 * method. This is an explicit boolean gate, not exception-based control flow:
 * <ul>
 *   <li>{@code isEnabled() == false} (NoOp shipped default): the engine walks
 *       its existing {@code supplyAsync} execution path unchanged. Zero
 *       behaviour regression.</li>
 *   <li>{@code isEnabled() == true} (functional runtime, e.g.
 *       {@link InMemoryActorRuntime}): the engine additionally registers an
 *       Actor at execution start and destroys it at execution end.</li>
 * </ul>
 *
 * <h2>Identity model (foundational slice)</h2>
 * 1:1 mapping: at most one active actor per session at any time.
 * {@link #createActor(String, String)} is idempotent: calling it with a
 * session that already has an active actor (status
 * {@code CREATED/READY/RUNNING/IDLE/RECOVERING}) returns the existing
 * instance. Calling it for a session whose prior actor is in a terminal
 * state ({@code FAILED/STOPPED}) creates a fresh instance.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent {@link #createActor} /
 * {@link #destroyActor} / query calls from multiple threads.
 * {@link InMemoryActorRuntime} achieves this via a {@link ActorRegistry}
 * backed by {@code ConcurrentHashMap} plus per-actor single-thread executors.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #createActor} — create/register an Actor, bind mailbox, start
 *       consumption loop. Returns the Actor instance.</li>
 *   <li>{@link #getActor} / {@link #getActorBySession} / {@link #getActiveActors}
 *       — query the registry.</li>
 *   <li>{@link #destroyActor} — gracefully stop a single Actor (status →
 *       {@code STOPPED}, thread interrupted, registry entry removed).</li>
 *   <li>{@link #destroyAll} — gracefully stop every active Actor; returns the
 *       count destroyed.</li>
 * </ol>
 *
 * <p>See plan 218 (L4-8) and vision §3.1/§4.2.
 */
public interface IActorRuntime {

    /**
     * Whether this runtime is functional. The engine uses this as an explicit
     * gate before calling any lifecycle method.
     *
     * @return {@code true} for functional implementations (e.g.
     *         {@link InMemoryActorRuntime}); {@code false} for
     *         {@link NoOpActorRuntime} (shipped default — engine skips the
     *         Actor path entirely)
     */
    boolean isEnabled();

    /**
     * Create (or reuse) an Actor for the given session. Idempotent: if the
     * session already has an active actor, the existing instance is returned.
     *
     * <p>When a functional runtime is enabled, this method:
     * <ol>
     *   <li>Creates an {@link AgentActor} with a fresh UUID actorId.</li>
     *   <li>Transitions status {@code CREATED → READY}.</li>
     *   <li>Registers the actor in the {@link ActorRegistry}.</li>
     *   <li>Binds the session's existing {@link io.nop.ai.agent.message.IMailbox}
     *       (if one was created by the engine) for observation-only consumption.</li>
     *   <li>Starts a dedicated single-thread executor running the consumption loop.</li>
     * </ol>
     *
     * @param sessionId the persistent session identity
     * @param agentName the static agent configuration name
     * @return the created or reused Actor instance
     */
    AgentActor createActor(String sessionId, String agentName);

    /**
     * Look up an actor by its runtime instance identity.
     *
     * @param actorId the UUID actor identity
     * @return the actor, or empty if no actor with this id is registered
     */
    Optional<AgentActor> getActor(String actorId);

    /**
     * Look up an actor by its bound session identity.
     *
     * @param sessionId the persistent session identity
     * @return the actor bound to this session, or empty if none is registered
     */
    Optional<AgentActor> getActorBySession(String sessionId);

    /**
     * @return a snapshot collection of all currently active actors (status
     *         not {@code STOPPED}). Never null; empty when no actors are
     *         active.
     */
    Collection<AgentActor> getActiveActors();

    /**
     * Gracefully stop a single Actor: transition status to {@code STOPPED},
     * interrupt the consumption-loop thread, and remove the actor from the
     * registry.
     *
     * @param actorId the UUID actor identity
     * @return {@code true} if an actor was found and destroyed;
     *         {@code false} if no actor with this id is registered
     */
    boolean destroyActor(String actorId);

    /**
     * Gracefully stop every active Actor.
     *
     * @return the number of actors destroyed
     */
    int destroyAll();
}
