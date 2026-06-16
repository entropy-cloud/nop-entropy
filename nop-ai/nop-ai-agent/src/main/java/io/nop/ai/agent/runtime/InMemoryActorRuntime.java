package io.nop.ai.agent.runtime;

import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Functional {@link IActorRuntime} that manages {@link AgentActor} instances
 * with per-actor dedicated single-thread executors running an
 * <strong>observation-only</strong> mailbox consumption loop.
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
 * <h2>Observation-only consumption loop (裁定 6)</h2>
 * The loop runs on a dedicated single-thread executor per actor:
 * <ol>
 *   <li>Transition {@code READY → RUNNING} on the first poll.</li>
 *   <li>{@code mailbox.poll()} — if a message is available:
 *     <ul>
 *       <li>Record the entry in {@code actor.receivedMessages} (observation log).</li>
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
 * <p><strong>The loop does NOT inject messages into the ReAct execution
 * context or session.</strong> It only records + logs + acks. Steering
 * injection (modifying the in-flight ReAct execution) is an explicit
 * successor.
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
 * <p>See plan 218 (L4-8) and vision §3.1/§4.2.
 */
public final class InMemoryActorRuntime implements IActorRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryActorRuntime.class);

    /** Default poll interval (milliseconds) when the mailbox is empty. */
    public static final long DEFAULT_POLL_INTERVAL_MS = 1000L;

    /** Default graceful shutdown timeout (milliseconds) for executor shutdown. */
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 5000L;

    private final ActorRegistry registry;
    private final Function<String, IMailbox> mailboxLookup;
    private final long pollIntervalMs;
    private final long shutdownTimeoutMs;

    private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

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
        this.registry = new InMemoryActorRegistry();
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

        registry.unregister(actorId);
        LOG.debug("InMemoryActorRuntime: destroyed actor actorId={}, sessionId={}",
                actorId, actor.getSessionId());
        return true;
    }

    /**
     * The observation-only mailbox consumption loop. Runs on the actor's
     * dedicated single thread.
     */
    private void runConsumptionLoop(AgentActor actor) {
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
                        // Observation-only: record + log (NO injection into session/ctx).
                        actor.addReceivedMessage(entry);
                        LOG.info("AgentActor received message: actorId={}, sessionId={}, deliveryId={}, "
                                        + "kind={}, senderId={}, payload={}",
                                actor.getActorId(), actor.getSessionId(), entry.getDeliveryId(),
                                entry.getEnvelope().getKind(), entry.getEnvelope().getSenderId(),
                                entry.getEnvelope().getPayload());
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

    private static boolean isActiveStatus(AgentActorStatus status) {
        return status == AgentActorStatus.CREATED
                || status == AgentActorStatus.READY
                || status == AgentActorStatus.RUNNING
                || status == AgentActorStatus.IDLE
                || status == AgentActorStatus.RECOVERING;
    }
}
