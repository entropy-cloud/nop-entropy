package io.nop.ai.agent.runtime;

import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxEntry;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * Runtime instance of an Agent executing inside an {@link IActorRuntime}
 * container. Each Actor wraps a single agent execution session, optionally
 * consumes messages from a deferred-ack {@link IMailbox}, and exposes
 * observable status through the {@link ActorRegistry}.
 *
 * <h2>Identity model (immutable)</h2>
 * <ul>
 *   <li>{@code actorId} — UUID generated at creation time; the runtime
 *       instance identity.</li>
 *   <li>{@code sessionId} — the persistent session identity this actor is
 *       bound to (1:1 mapping in the foundational slice: at most one active
 *       actor per session).</li>
 *   <li>{@code agentName} — the static configuration name of the agent
 *       ({@code agent.xml} name).</li>
 *   <li>{@code createdAt} — wall-clock timestamp (millis) at creation.</li>
 * </ul>
 *
 * <h2>Runtime state (volatile / thread-safe)</h2>
 * <ul>
 *   <li>{@code status} — {@link AgentActorStatus}, {@code volatile} for
 *       cross-thread visibility (the consumption loop writes, external
 *       observers read).</li>
 *   <li>{@code lastActiveAt} — wall-clock timestamp (millis) updated on
 *       every poll / message processing; {@code volatile} for
 *       cross-thread visibility.</li>
 *   <li>{@code receivedMessages} — observation log of messages consumed
 *       from the mailbox (poll → record → ack). Guarded by a synchronized
 *       wrapper; written from the Actor's single consumption thread, read
 *       from any thread via {@link #getReceivedMessages()} snapshot.
 *       Retained for observability even when steering injection is active
 *       (plan 220).</li>
 * </ul>
 *
 * <h2>Mailbox reference (nullable)</h2>
 * The optional {@link IMailbox} is set at creation time. When non-null, the
 * Actor's consumption loop polls it. When null (no mailbox created for the
 * session), the loop idles without polling.
 *
 * <h2>Steering queue reference (nullable, volatile)</h2>
 * Plan 220 (L4-8-steering): the optional steering queue is a reference to the
 * {@link io.nop.ai.agent.engine.AgentExecutionContext}'s thread-safe queue,
 * bound by the engine AFTER {@code createActor} returns but BEFORE
 * {@code ReActAgentExecutor.execute(ctx)} is called. When non-null, the
 * consumption loop converts each polled mailbox envelope payload into a
 * {@link ChatMessage} and enqueues it into this queue (steering injection),
 * so the ReAct loop can drain it at the round boundary and feed it into the
 * next LLM call. When null (the Actor's consumption loop polled a message
 * before the engine bound the queue), the loop degrades to observation-only
 * (record + ack, never discards the message). The field is {@code volatile}
 * because the engine (ReAct / supplyAsync) thread writes it and the Actor's
 * consumption thread reads it.
 *
 * <p><b>Thread-safety contract</b>: {@code status} and {@code lastActiveAt}
 * are {@code volatile} and safe to read from any thread. The
 * {@code receivedMessages} list is written exclusively from the Actor's single
 * consumption thread and exposed to external readers as a synchronized
 * snapshot copy. The {@code steeringQueue} reference is {@code volatile}. All
 * other fields are immutable after construction.
 *
 * <p>See plan 218 (L4-8) and vision §3.1.
 */
public final class AgentActor {

    private final String actorId;
    private final String sessionId;
    private final String agentName;
    private final long createdAt;
    private final IMailbox mailbox;

    private volatile AgentActorStatus status;
    private volatile long lastActiveAt;

    private final List<MailboxEntry> receivedMessages = Collections.synchronizedList(new ArrayList<>());

    // Plan 220 (L4-8-steering): optional reference to the ctx steering queue.
    // Bound by the engine after createActor returns, before execute(ctx). See
    // class Javadoc for the full thread-safety / null-degradation contract.
    private volatile Queue<ChatMessage> steeringQueue;

    /**
     * Create a new Actor instance.
     *
     * @param actorId   UUID-generated runtime instance identity
     * @param sessionId persistent session identity (1:1 with this actor in the
     *                  foundational slice)
     * @param agentName static agent configuration name
     * @param createdAt wall-clock timestamp (millis) at creation
     * @param mailbox   optional deferred-ack mailbox for observation-only
     *                  consumption ({@code null} = no mailbox consumption)
     */
    public AgentActor(String actorId, String sessionId, String agentName,
                      long createdAt, IMailbox mailbox) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.agentName = Objects.requireNonNull(agentName, "agentName");
        this.createdAt = createdAt;
        this.mailbox = mailbox;
        this.status = AgentActorStatus.CREATED;
        this.lastActiveAt = createdAt;
    }

    /**
     * @return the UUID-generated runtime instance identity.
     */
    public String getActorId() {
        return actorId;
    }

    /**
     * @return the persistent session identity this actor is bound to.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * @return the static agent configuration name.
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * @return wall-clock timestamp (millis) at creation.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * @return the current lifecycle status (volatile, safe for cross-thread reads).
     */
    public AgentActorStatus getStatus() {
        return status;
    }

    /**
     * Transition the actor's status. No validation of transition legality is
     * performed — callers ({@code IActorRuntime} and the consumption loop)
     * are responsible for following the state machine documented in
     * {@link AgentActorStatus}. The assignment is visible across threads via
     * {@code volatile}.
     *
     * @param newStatus the target status (must not be null)
     */
    public void updateStatus(AgentActorStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
    }

    /**
     * @return wall-clock timestamp (millis) of the last activity (poll /
     *         message processing); volatile, safe for cross-thread reads.
     */
    public long getLastActiveAt() {
        return lastActiveAt;
    }

    /**
     * Update {@code lastActiveAt} to the current wall-clock time. Called by
     * the consumption loop on every poll / message processing cycle.
     */
    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * @return the deferred-ack mailbox bound to this actor, or {@code null}
     *         if the session has no mailbox (no consumption in the loop).
     */
    public IMailbox getMailbox() {
        return mailbox;
    }

    /**
     * Append a consumed mailbox entry to the observation-only received-messages
     * log. Called exclusively from the Actor's single consumption thread.
     *
     * @param entry the polled entry to record (must not be null)
     */
    public void addReceivedMessage(MailboxEntry entry) {
        receivedMessages.add(Objects.requireNonNull(entry, "entry"));
    }

    /**
     * @return a snapshot copy of the observation-only received-messages log.
     *         Safe to call from any thread; the returned list is a defensive
     *         copy and will not reflect subsequent additions.
     */
    public List<MailboxEntry> getReceivedMessages() {
        synchronized (receivedMessages) {
            return new ArrayList<>(receivedMessages);
        }
    }

    /**
     * Plan 220 (L4-8-steering): bind the steering queue reference. Called by
     * the engine (on the ReAct / supplyAsync thread) AFTER {@code createActor}
     * returns and BEFORE {@code ReActAgentExecutor.execute(ctx)} is invoked.
     * The bound queue is the {@link
     * io.nop.ai.agent.engine.AgentExecutionContext}'s live
     * {@code ConcurrentLinkedQueue}, so messages enqueued here by the Actor's
     * consumption thread are visible to the ReAct loop's drain. The assignment
     * is {@code volatile} so the Actor's consumption thread observes it
     * without synchronization.
     *
     * @param steeringQueue the ctx steering queue to bind (may be null to
     *                      unbind / force observation-only degradation)
     */
    public void setSteeringQueue(Queue<ChatMessage> steeringQueue) {
        this.steeringQueue = steeringQueue;
    }

    /**
     * Plan 220 (L4-8-steering): the bound steering queue reference, or
     * {@code null} when not yet bound. The Actor's consumption loop reads this
     * on every polled message: a non-null return enables steering injection
     * (enqueue converted {@link ChatMessage}); a null return degrades to
     * observation-only (record + ack, never discards the message).
     *
     * @return the bound steering queue, or {@code null} if not bound
     */
    public Queue<ChatMessage> getSteeringQueue() {
        return steeringQueue;
    }

    @Override
    public String toString() {
        return "AgentActor{actorId='" + actorId + "', sessionId='" + sessionId
                + "', agentName='" + agentName + "', status=" + status
                + ", createdAt=" + createdAt + ", lastActiveAt=" + lastActiveAt
                + ", hasMailbox=" + (mailbox != null) + '}';
    }
}
