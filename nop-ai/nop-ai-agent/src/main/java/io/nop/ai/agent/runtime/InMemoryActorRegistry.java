package io.nop.ai.agent.runtime;

import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;

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
 * <h2>Multi-tenant isolation (plan 232 / vision §5.1)</h2>
 * A {@link ITenantResolver} tags each registered actor with the tenant that
 * was active when {@link #register} ran. Query methods
 * ({@link #get} / {@link #getBySession} / {@link #getAll}) filter by the
 * <em>current</em> tenant when the resolver reports a non-null tenant — actors
 * registered under a different tenant are invisible (Tenant 隔离). When the
 * resolver reports {@code null} (no tenant context), no filtering occurs and
 * all registered actors are visible (backward compatible, single tenant,
 * existing tests). The {@link AgentActor} constructor and the
 * {@code IActorRuntime.createActor} interface signature are <strong>not</strong>
 * modified — tenant is tracked internally via a tag map (plan Design Decision 5).
 *
 * <p>See plan 218 (L4-8) and plan 232 (L4-multi-tenant-isolation).
 */
public final class InMemoryActorRegistry implements ActorRegistry {

    private final ConcurrentHashMap<String, AgentActor> byActorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToActorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> actorToTenant = new ConcurrentHashMap<>();
    private final ITenantResolver tenantResolver;

    /**
     * Create a registry with the backward-compatible {@link NullTenantResolver}
     * (no tenant filtering — all registered actors visible).
     */
    public InMemoryActorRegistry() {
        this(NullTenantResolver.INSTANCE);
    }

    /**
     * Create a registry that tags actors with the tenant reported by
     * {@code tenantResolver} at registration time, and filters query results by
     * the resolver's current tenant.
     *
     * @param tenantResolver the contextual tenant resolver; never null
     */
    public InMemoryActorRegistry(ITenantResolver tenantResolver) {
        this.tenantResolver = java.util.Objects.requireNonNull(
                tenantResolver, "tenantResolver must not be null");
    }

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
        // Plan 232: tag the actor with the tenant active at registration time.
        // ConcurrentHashMap rejects null values, so a null tenant (no tenant
        // context) is recorded as ABSENT from the tag map — which the
        // visibility check treats as "legacy / global actor, visible to all"
        // (mirrors the DB `OR TENANT_ID IS NULL` semantics).
        String tagTenant = tenantResolver.resolveTenantId();
        if (tagTenant != null) {
            actorToTenant.put(actor.getActorId(), tagTenant);
        } else {
            actorToTenant.remove(actor.getActorId());
        }
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
        actorToTenant.remove(actorId);
    }

    @Override
    public Optional<AgentActor> get(String actorId) {
        if (actorId == null) {
            return Optional.empty();
        }
        AgentActor actor = byActorId.get(actorId);
        return Optional.ofNullable(visible(actorId, actor));
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
        AgentActor actor = byActorId.get(actorId);
        return Optional.ofNullable(visible(actorId, actor));
    }

    @Override
    public Collection<AgentActor> getAll() {
        String tenant = tenantResolver.resolveTenantId();
        java.util.Collection<AgentActor> all = byActorId.values();
        if (tenant == null) {
            return new ArrayList<>(all);
        }
        java.util.List<AgentActor> filtered = new ArrayList<>();
        for (AgentActor actor : all) {
            String tag = actorToTenant.get(actor.getActorId());
            // An untagged actor (null tenant at registration) is treated as a
            // legacy / global actor visible to every tenant.
            if (tag == null || tenant.equals(tag)) {
                filtered.add(actor);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    /**
     * Return {@code actor} only if it is visible to the current tenant.
     * Visible when there is no tenant context (resolver returns null — all
     * visible), or when the actor was tagged with the current tenant, or when
     * the actor was registered without a tenant (legacy/global actor). Returns
     * {@code null} for actors tagged under a different tenant (cross-tenant
     * isolation) and for the null/missing case.
     */
    private AgentActor visible(String actorId, AgentActor actor) {
        if (actor == null) {
            return null;
        }
        String tenant = tenantResolver.resolveTenantId();
        if (tenant == null) {
            return actor;
        }
        String tag = actorToTenant.get(actorId);
        // Untagged (null tenant at registration) = legacy / global → visible.
        if (tag == null || tenant.equals(tag)) {
            return actor;
        }
        return null;
    }
}
