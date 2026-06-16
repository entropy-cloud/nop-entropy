package io.nop.ai.agent.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Shipped no-op default for {@link IActorRuntime}.
 *
 * <p>{@link #isEnabled()} returns {@code false} — the engine uses this as an
 * explicit gate to skip the Actor path entirely (no exceptions, no silent
 * side effects, no performance overhead). {@link #createActor(String, String)}
 * throws {@link UnsupportedOperationException} purely as a defensive measure:
 * the engine never calls it when {@code isEnabled() == false}, but a direct
 * caller bypassing the gate gets a fast failure instead of a silent no-op.
 *
 * <p>All query methods return empty results and {@link #destroyActor} /
 * {@link #destroyAll} return {@code false} / {@code 0} — consistent with the
 * "no actors exist" semantics.
 *
 * <p>The engine uses this default out-of-the-box, so integrators see zero
 * behaviour regression unless they explicitly wire a functional runtime
 * (e.g. {@link InMemoryActorRuntime}) via
 * {@code DefaultAgentEngine.setActorRuntime(...)}.
 *
 * <p>See plan 218 (L4-8) and Minimum Rules #24 (No Silent No-Op).
 */
public final class NoOpActorRuntime implements IActorRuntime {

    private static final NoOpActorRuntime INSTANCE = new NoOpActorRuntime();

    private NoOpActorRuntime() {
    }

    public static NoOpActorRuntime noOp() {
        return INSTANCE;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AgentActor createActor(String sessionId, String agentName) {
        throw new UnsupportedOperationException(
                "NoOpActorRuntime.createActor: actor runtime is not enabled "
                        + "(wire InMemoryActorRuntime via DefaultAgentEngine.setActorRuntime to enable)");
    }

    @Override
    public Optional<AgentActor> getActor(String actorId) {
        return Optional.empty();
    }

    @Override
    public Optional<AgentActor> getActorBySession(String sessionId) {
        return Optional.empty();
    }

    @Override
    public Collection<AgentActor> getActiveActors() {
        return Collections.emptyList();
    }

    @Override
    public boolean destroyActor(String actorId) {
        return false;
    }

    @Override
    public int destroyAll() {
        return 0;
    }
}
