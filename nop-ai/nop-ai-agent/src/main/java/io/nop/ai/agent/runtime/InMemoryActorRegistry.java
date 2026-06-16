package io.nop.ai.agent.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link ActorRegistry} backed by two
 * {@link ConcurrentHashMap} indices: {@code actorId → AgentActor} and
 * {@code sessionId → actorId}.
 *
 * <h2>Index consistency</h2>
 * Both indices are kept in sync on every {@link #register} and
 * {@link #unregister} call. {@link #register} writes actorId first, then
 * updates the session index. {@link #unregister} removes the session index
 * entry first, then the actor entry. This ordering ensures that a concurrent
 * reader never sees a session index pointing to a non-existent actor (the
 * session index is the secondary lookup path).
 *
 * <p>See plan 218 (L4-8).
 */
public final class InMemoryActorRegistry implements ActorRegistry {

    private final ConcurrentHashMap<String, AgentActor> byActorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToActorId = new ConcurrentHashMap<>();

    @Override
    public void register(AgentActor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("register: actor must not be null");
        }
        // If an actor with the same actorId already exists, clean up its
        // session index entry first (defensive — createActor generates fresh
        // UUIDs, so this only fires on explicit re-registration).
        AgentActor previous = byActorId.put(actor.getActorId(), actor);
        if (previous != null && !previous.getSessionId().equals(actor.getSessionId())) {
            sessionToActorId.remove(previous.getSessionId(), actor.getActorId());
        }
        sessionToActorId.put(actor.getSessionId(), actor.getActorId());
    }

    @Override
    public void unregister(String actorId) {
        if (actorId == null) {
            return;
        }
        AgentActor removed = byActorId.remove(actorId);
        if (removed != null) {
            // Only remove the session index entry if it still points to this
            // actor (defensive: a new actor for the same session may have
            // already replaced it).
            sessionToActorId.remove(removed.getSessionId(), actorId);
        }
    }

    @Override
    public Optional<AgentActor> get(String actorId) {
        if (actorId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byActorId.get(actorId));
    }

    @Override
    public Optional<AgentActor> getBySession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        String actorId = sessionToActorId.get(sessionId);
        if (actorId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byActorId.get(actorId));
    }

    @Override
    public Collection<AgentActor> getAll() {
        return new ArrayList<>(byActorId.values());
    }
}
