package io.nop.ai.agent.runtime;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxEntry;
import io.nop.ai.agent.quota.IResourceGuard;
import io.nop.ai.agent.quota.NoOpResourceGuard;
import io.nop.ai.agent.quota.QuotaDecision;
import io.nop.ai.agent.quota.QuotaDimension;
import io.nop.ai.agent.security.ITenantResolver;
import io.nop.ai.agent.security.NullTenantResolver;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Functional {@link IActorRuntime} that manages {@link AgentActor} instances
 * with per-actor dedicated single-thread executors running a
 * <strong>steering-injection</strong> mailbox consumption loop.
 *
 * <h2>Opt-in contract</h2>
 * {@link #isEnabled()} returns {@code true}. The engine gates on this before
 * calling any lifecycle method (shipped default {@link NoOpActorRuntime}
 * returns {@code false} → engine skips the Actor path entirely).
 *
 * <h2>Mailbox lookup dependency</h2>
 * The runtime does not create mailboxes — it consumes mailboxes that the
 * engine has already created for each session. The {@code mailboxLookup}
 * function (typically bound to {@code DefaultAgentEngine::getSessionMailbox})
 * retrieves the existing per-session {@link IMailbox}. When the lookup
 * returns {@code null} (no mailbox created for the session, e.g. no
 * mailboxFactory wired), the Actor's consumption loop idles without polling
 * — graceful, no exception.
 *
 * <h2>Steering-injection consumption loop (plan 220 / L4-8-steering)</h2>
 * The loop runs on a dedicated single-thread executor per actor:
 * <ol>
 *   <li>Transition {@code READY → RUNNING} on the first poll.</li>
 *   <li>{@code mailbox.poll()} — if a message is available:
 *     <ul>
 *       <li>Record the entry in {@code actor.receivedMessages} (observation log).</li>
 *       <li>If the Actor's steering queue reference is bound (non-null),
 *           convert the envelope payload to a {@link ChatUserMessage} and
 *           enqueue it into the ctx steering queue (steering injection). The
 *           queue is the live {@link io.nop.ai.agent.engine.AgentExecutionContext}
 *           steering queue, bound by the engine via
 *           {@code actor.setSteeringQueue(ctx.getSteeringQueue())} before
 *           {@code execute(ctx)} is called.</li>
 *       <li>If the steering queue reference is null (not yet bound or
 *           unbound), degrade to observation-only: the message is recorded
 *           but NOT injected (裁定 2 null 退路).</li>
 *       <li>Log at INFO level (visible observation).</li>
 *       <li>{@code mailbox.ack(deliveryId)} — confirm processing.</li>
 *       <li>Transition {@code IDLE → RUNNING} if currently IDLE.</li>
 *     </ul>
 *   </li>
 *   <li>If no message (poll returns null): transition {@code RUNNING → IDLE},
 *       sleep for {@code pollIntervalMs}.</li>
 *   <li>Loop exit: actor status is {@code STOPPED}/{@code FAILED} or the
 *       thread is interrupted ({@code destroyActor}).</li>
 *   <li>Exception in poll/ack: log at ERROR + transition to {@code FAILED}
 *       (never silently swallowed).</li>
 * </ol>
 *
 * <p>The loop injects polled messages into the ReAct execution context's
 * steering queue. The ReAct loop drains the queue at the round boundary
 * (after all tools complete, before the next LLM call) and appends the
 * steering messages to the ctx message list, so the LLM sees them in the
 * next iteration. With the shipped NoOpActorRuntime default no consumption
 * loop runs, so the steering queue stays empty.
 *
 * <h2>Thread safety</h2>
 * The registry ({@link InMemoryActorRegistry}) is backed by
 * {@code ConcurrentHashMap}. Per-actor executors are stored in a separate
 * {@code ConcurrentHashMap}. Each actor's consumption loop runs on a single
 * dedicated thread, so there is no intra-actor concurrency.
 *
 * <h2>Java 11 compatibility</h2>
 * Uses {@link Executors#newSingleThreadExecutor()} per actor (standard Java
 * 11 concurrency). Virtual Thread ({@code Thread.ofVirtual()}) optimization
 * is a successor that depends on the module migrating to Java 21.
 *
 * <p>See plan 218 (L4-8), plan 220 (L4-8-steering), and vision §3.1/§4.2/§10.
 */
public final class InMemoryActorRuntime implements IActorRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryActorRuntime.class);

    /** Default poll interval (milliseconds) when the mailbox is empty. */
    public static final long DEFAULT_POLL_INTERVAL_MS = 1000L;

    /** Default graceful shutdown timeout (milliseconds) for executor shutdown. */
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 5000L;

    private final ActorRegistry registry;
    private final ITenantResolver tenantResolver;
    private final IResourceGuard resourceGuard;
    private final Function<String, IMailbox> mailboxLookup;
    private final long pollIntervalMs;
    private final long shutdownTimeoutMs;

    private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    /**
     * Sentinel scopeKey for the global bucket when no tenant context is
     * active (plan 234 Design Decision §5 — null tenant = single global
     * bucket).
     */
    private static final String GLOBAL_SCOPE = "__global__";

    /**
     * Create a runtime with the default poll interval (1000ms) and shutdown
     * timeout (5000ms).
     *
     * @param mailboxLookup function that returns the engine-created per-session
     *                      mailbox (or null if none); typically bound to
     *                      {@code engine::getSessionMailbox}
     */
    public InMemoryActorRuntime(Function<String, IMailbox> mailboxLookup) {
        this(mailboxLookup, DEFAULT_POLL_INTERVAL_MS, DEFAULT_SHUTDOWN_TIMEOUT_MS);
    }

    /**
     * Create a runtime with the default poll interval (1000ms) and shutdown
     * timeout (5000ms), and a contextual tenant resolver for registry tenant
     * isolation (plan 232 / vision §5.1).
     *
     * @param mailboxLookup  function that returns the engine-created per-session
     *                       mailbox (or null if none); typically bound to
     *                       {@code engine::getSessionMailbox}
     * @param tenantResolver the contextual tenant resolver used to tag/filter
     *                       registered actors; never null (use
     *                       {@link NullTenantResolver#INSTANCE} to disable
     *                       tenant filtering)
     */
    public InMemoryActorRuntime(Function<String, IMailbox> mailboxLookup,
                                ITenantResolver tenantResolver) {
        this(mailboxLookup, tenantResolver, DEFAULT_POLL_INTERVAL_MS, DEFAULT_SHUTDOWN_TIMEOUT_MS);
    }

    /**
     * Create a runtime with an explicit poll interval (for tests) and
     * shutdown timeout.
     *
     * @param mailboxLookup      function that returns the engine-created per-session
     *                           mailbox (or null if none)
     * @param pollIntervalMs     sleep duration (ms) when the mailbox is empty
     * @param shutdownTimeoutMs  graceful shutdown timeout (ms) for executor shutdown
     */
    public InMemoryActorRuntime(Function<String, IMailbox> mailboxLookup,
                                long pollIntervalMs, long shutdownTimeoutMs) {
        this(mailboxLookup, NullTenantResolver.INSTANCE, pollIntervalMs, shutdownTimeoutMs);
    }

    /**
     * Create a runtime with an explicit poll interval, shutdown timeout, and a
     * contextual tenant resolver for registry tenant isolation (plan 232).
     *
     * @param mailboxLookup      function that returns the engine-created per-session
     *                           mailbox (or null if none)
     * @param tenantResolver     the contextual tenant resolver used to tag/filter
     *                           registered actors; never null
     * @param pollIntervalMs     sleep duration (ms) when the mailbox is empty
     * @param shutdownTimeoutMs  graceful shutdown timeout (ms) for executor shutdown
     */
    public InMemoryActorRuntime(Function<String, IMailbox> mailboxLookup,
                                ITenantResolver tenantResolver,
                                long pollIntervalMs, long shutdownTimeoutMs) {
        this(mailboxLookup, tenantResolver, pollIntervalMs, shutdownTimeoutMs, NoOpResourceGuard.noOp());
    }

    /**
     * Create a runtime with an explicit poll interval, shutdown timeout, a
     * contextual tenant resolver, and a quota guard (plan 234 / vision §5.2).
     * When a {@link io.nop.ai.agent.quota.DefaultResourceGuard} is supplied,
     * {@code createActor} enforces
     * {@link QuotaDimension#CONCURRENT_ACTORS_PER_TENANT} — the projected
     * per-tenant active actor count is checked against the guard before a new
     * actor is registered.
     *
     * @param mailboxLookup      function that returns the engine-created per-session
     *                           mailbox (or null if none)
     * @param tenantResolver     the contextual tenant resolver used to tag/filter
     *                           registered actors; never null
     * @param pollIntervalMs     sleep duration (ms) when the mailbox is empty
     * @param shutdownTimeoutMs  graceful shutdown timeout (ms) for executor shutdown
     * @param resourceGuard      the quota-decision gateway; {@code null} falls back
     *                           to {@link NoOpResourceGuard}
     */
    public InMemoryActorRuntime(Function<String, IMailbox> mailboxLookup,
                                ITenantResolver tenantResolver,
                                long pollIntervalMs, long shutdownTimeoutMs,
                                IResourceGuard resourceGuard) {
        this.registry = new InMemoryActorRegistry(tenantResolver);
        this.tenantResolver = Objects.requireNonNull(tenantResolver, "tenantResolver");
        this.resourceGuard = resourceGuard != null ? resourceGuard : NoOpResourceGuard.noOp();
        this.mailboxLookup = mailboxLookup;
        this.pollIntervalMs = pollIntervalMs;
        this.shutdownTimeoutMs = shutdownTimeoutMs;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public AgentActor createActor(String sessionId, String agentName) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("createActor: sessionId must not be null or empty");
        }
        if (agentName == null || agentName.isEmpty()) {
            throw new IllegalArgumentException("createActor: agentName must not be null or empty");
        }

        // Idempotent: return existing active actor for the same session.
        Optional<AgentActor> existing = registry.getBySession(sessionId);
        if (existing.isPresent()) {
            AgentActor existingActor = existing.get();
            AgentActorStatus status = existingActor.getStatus();
            if (isActiveStatus(status)) {
                return existingActor;
            }
            // Terminal status (FAILED/STOPPED) → unregister old, create fresh.
            destroyActorInternal(existingActor.getActorId());
        }

        // Plan 234: enforce CONCURRENT_ACTORS_PER_TENANT quota before creating
        // a new actor (config-driven; override <= 0). The scopeKey is the
        // tenant resolved by the contextual ITenantResolver (Design Decision
        // §5); when no tenant context is active, a single global bucket is
        // used. The projected count = the current active actor count for the
        // scope + 1. Only effective when a functional guard is wired (NoOp
        // always allows); the runtime's isEnabled()==true gate is enforced by
        // the engine before createActor is ever called.
        String tenant = tenantResolver.resolveTenantId();
        String scopeKey = tenant != null ? tenant : GLOBAL_SCOPE;
        // getActiveActors() is tenant-filtered via the registry, so its size
        // is the current per-tenant (or global) active actor count.
        int activeCount = getActiveActors().size();
        QuotaDecision decision = resourceGuard.checkConcurrent(
                QuotaDimension.CONCURRENT_ACTORS_PER_TENANT, scopeKey,
                activeCount + 1, 0);
        if (!decision.isAllowed()) {
            throw new NopAiAgentException(
                    "InMemoryActorRuntime.createActor denied by quota: "
                            + decision.getReason());
        }

        String actorId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        IMailbox mailbox = null;
        try {
            mailbox = mailboxLookup.apply(sessionId);
        } catch (Exception e) {
            LOG.warn("InMemoryActorRuntime: mailboxLookup threw for sessionId={}", sessionId, e);
        }

        AgentActor actor = new AgentActor(actorId, sessionId, agentName, now, mailbox);
        actor.updateStatus(AgentActorStatus.READY);
        registry.register(actor);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "agent-actor-" + actorId);
            t.setDaemon(true);
            return t;
        });
        executors.put(actorId, executor);
        executor.submit(() -> runConsumptionLoop(actor));

        LOG.debug("InMemoryActorRuntime: created actor actorId={}, sessionId={}, agentName={}, hasMailbox={}",
                actorId, sessionId, agentName, mailbox != null);
        return actor;
    }

    @Override
    public Optional<AgentActor> getActor(String actorId) {
        return registry.get(actorId);
    }

    @Override
    public Optional<AgentActor> getActorBySession(String sessionId) {
        return registry.getBySession(sessionId);
    }

    @Override
    public Collection<AgentActor> getActiveActors() {
        Collection<AgentActor> all = registry.getAll();
        Collection<AgentActor> active = new ArrayList<>();
        for (AgentActor actor : all) {
            if (isActiveStatus(actor.getStatus())) {
                active.add(actor);
            }
        }
        return Collections.unmodifiableCollection(active);
    }

    @Override
    public boolean destroyActor(String actorId) {
        if (actorId == null) {
            return false;
        }
        Optional<AgentActor> opt = registry.get(actorId);
        if (opt.isEmpty()) {
            return false;
        }
        return destroyActorInternal(actorId);
    }

    @Override
    public int destroyAll() {
        Collection<AgentActor> all = registry.getAll();
        int count = 0;
        for (AgentActor actor : all) {
            if (destroyActorInternal(actor.getActorId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Internal destroy: set status STOPPED, shutdown executor (interrupts the
     * consumption-loop thread), unregister from registry.
     */
    private boolean destroyActorInternal(String actorId) {
        Optional<AgentActor> opt = registry.get(actorId);
        if (opt.isEmpty()) {
            return false;
        }
        AgentActor actor = opt.get();
        actor.updateStatus(AgentActorStatus.STOPPED);

        ExecutorService executor = executors.remove(actorId);
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(shutdownTimeoutMs, TimeUnit.MILLISECONDS)) {
                    LOG.warn("InMemoryActorRuntime: executor for actorId={} did not terminate within {}ms",
                            actorId, shutdownTimeoutMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("InMemoryActorRuntime: interrupted while waiting for executor termination, actorId={}",
                        actorId);
            }
        }

        // Re-assert STOPPED after executor termination: the consumption
        // loop's initial RUNNING transition may have overwritten the STOPPED
        // status set above if the loop thread started executing between our
        // status set and shutdownNow(). After awaitTermination the loop
        // thread has exited, so this re-assertion is race-free.
        actor.updateStatus(AgentActorStatus.STOPPED);

        registry.unregister(actorId);
        LOG.debug("InMemoryActorRuntime: destroyed actor actorId={}, sessionId={}",
                actorId, actor.getSessionId());
        return true;
    }

    /**
     * The steering-injection mailbox consumption loop (plan 220 / L4-8-steering).
     * Runs on the actor's dedicated single thread. When the Actor's steering
     * queue reference is bound (non-null), polled messages are converted to
     * {@link ChatUserMessage} and enqueued into the ctx steering queue before
     * ack. When the steering queue reference is null (not yet bound), the loop
     * degrades to observation-only (record + ack, no injection — 裁定 2 null
     * 退路). poll/ack exceptions transition the Actor to FAILED (no silent
     * swallow).
     */
    private void runConsumptionLoop(AgentActor actor) {
        // Guard: if the actor was stopped/failed before the loop thread
        // started executing, exit immediately without transitioning to
        // RUNNING (avoids overwriting a terminal status set by destroyActor).
        AgentActorStatus initial = actor.getStatus();
        if (initial == AgentActorStatus.STOPPED || initial == AgentActorStatus.FAILED) {
            return;
        }
        // READY → RUNNING: the loop has started executing.
        actor.updateStatus(AgentActorStatus.RUNNING);
        IMailbox mailbox = actor.getMailbox();

        try {
            while (actor.getStatus() != AgentActorStatus.STOPPED
                    && actor.getStatus() != AgentActorStatus.FAILED
                    && !Thread.currentThread().isInterrupted()) {

                if (mailbox != null) {
                    MailboxEntry entry;
                    try {
                        entry = mailbox.poll();
                    } catch (Exception e) {
                        LOG.error("InMemoryActorRuntime: poll failed for actorId={}, transitioning to FAILED",
                                actor.getActorId(), e);
                        actor.updateStatus(AgentActorStatus.FAILED);
                        return;
                    }

                    if (entry != null) {
                        // Transition IDLE → RUNNING when a message arrives.
                        if (actor.getStatus() == AgentActorStatus.IDLE) {
                            actor.updateStatus(AgentActorStatus.RUNNING);
                        }
                        // Record the message for observability (always,
                        // regardless of steering-injection or observation-only).
                        actor.addReceivedMessage(entry);

                        // Plan 220 (L4-8-steering): steering-injection. When
                        // the Actor's steering queue reference is bound
                        // (non-null), convert the envelope payload to a
                        // ChatUserMessage and enqueue it into the ctx steering
                        // queue. The ReAct loop will drain the queue at the
                        // round boundary and append the message to the ctx
                        // message list for the next LLM call.
                        //
                        // When the steering queue reference is null (not yet
                        // bound by the engine, or the Actor was created
                        // outside the engine), degrade to observation-only:
                        // the message is recorded above but NOT injected
                        // (裁定 2 null 退路 — do not drop the message, just
                        // don't inject).
                        Queue<ChatMessage> steeringQueue = actor.getSteeringQueue();
                        if (steeringQueue != null) {
                            ChatMessage steeringMsg = toSteeringMessage(entry);
                            steeringQueue.add(steeringMsg);
                            LOG.info("AgentActor steering-injection: actorId={}, sessionId={}, "
                                            + "deliveryId={}, kind={}, senderId={}, payload={}",
                                    actor.getActorId(), actor.getSessionId(), entry.getDeliveryId(),
                                    entry.getEnvelope().getKind(), entry.getEnvelope().getSenderId(),
                                    entry.getEnvelope().getPayload());
                        } else {
                            LOG.info("AgentActor observation-only (steering queue not bound): "
                                            + "actorId={}, sessionId={}, deliveryId={}, payload={}",
                                    actor.getActorId(), actor.getSessionId(), entry.getDeliveryId(),
                                    entry.getEnvelope().getPayload());
                        }

                        try {
                            mailbox.ack(entry.getDeliveryId());
                        } catch (Exception e) {
                            LOG.error("InMemoryActorRuntime: ack failed for actorId={}, deliveryId={}, "
                                    + "transitioning to FAILED", actor.getActorId(), entry.getDeliveryId(), e);
                            actor.updateStatus(AgentActorStatus.FAILED);
                            return;
                        }
                        actor.touch();
                    } else {
                        // Empty poll → IDLE, then sleep.
                        if (actor.getStatus() == AgentActorStatus.RUNNING) {
                            actor.updateStatus(AgentActorStatus.IDLE);
                        }
                        actor.touch();
                        Thread.sleep(pollIntervalMs);
                    }
                } else {
                    // No mailbox bound → idle without polling.
                    if (actor.getStatus() == AgentActorStatus.RUNNING) {
                        actor.updateStatus(AgentActorStatus.IDLE);
                    }
                    actor.touch();
                    Thread.sleep(pollIntervalMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Normal exit: destroyActor interrupted the thread.
            if (actor.getStatus() != AgentActorStatus.STOPPED) {
                actor.updateStatus(AgentActorStatus.STOPPED);
            }
        } catch (Exception e) {
            LOG.error("InMemoryActorRuntime: consumption loop exception for actorId={}, "
                    + "transitioning to FAILED", actor.getActorId(), e);
            actor.updateStatus(AgentActorStatus.FAILED);
        }
    }

    /**
     * Plan 220 (L4-8-steering): convert a polled {@link MailboxEntry}'s
     * envelope payload to a {@link ChatUserMessage} for steering injection
     * (裁定 5: String payload → role=user). Non-String payloads are converted
     * via {@code String.valueOf}. The resulting message is enqueued into the
     * ctx steering queue and drained by the ReAct loop at the round boundary.
     */
    private static ChatMessage toSteeringMessage(MailboxEntry entry) {
        Object payload = entry.getEnvelope().getPayload();
        String content;
        if (payload == null) {
            content = "";
        } else if (payload instanceof String) {
            content = (String) payload;
        } else {
            content = String.valueOf(payload);
        }
        return new ChatUserMessage(content);
    }

    private static boolean isActiveStatus(AgentActorStatus status) {
        return status == AgentActorStatus.CREATED
                || status == AgentActorStatus.READY
                || status == AgentActorStatus.RUNNING
                || status == AgentActorStatus.IDLE
                || status == AgentActorStatus.RECOVERING;
    }
}
