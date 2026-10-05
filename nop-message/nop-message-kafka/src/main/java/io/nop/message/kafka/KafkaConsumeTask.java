/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.SeekMode;
import io.nop.api.core.message.PartialCommit;
import io.nop.api.core.message.TerminateConsume;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.util.FutureHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

/**
 * Kafka poll-loop consume task (mirrors {@code PulsarConsumeTask}). Runs on a dedicated
 * single-thread {@link ExecutorService}: polls the {@link Consumer}, builds an
 * {@link ApiMessage} per {@link ConsumerRecord} via {@link KafkaHelper}, invokes
 * {@link IMessageConsumer#onMessage} and dispatches the 5 documented return-value
 * semantics (null → commit, CompletionStage → await, ConsumeLater → seek no-commit,
 * Acknowledge → ack-topic reply + commit, other non-null → reply + commit).
 *
 * <p><strong>Seek policy (adjudication D3).</strong> Seek origins are resolved inside the
 * consumer group's rebalance callback ({@code onPartitionsAssigned}) for the freshly
 * assigned partitions — never before the poll loop, where the assignment is always empty
 * (MSG-F-01). INIT_SEEK modes apply only when the group has no committed offset for the
 * partition (first-ever consumption; a restart resumes from the committed offset), and
 * ALWAYS_SEEK_TO_END re-applies on every assignment. {@code seekToMessage} (offset-by-id)
 * is not supported and is rejected synchronously at subscribe time with
 * {@code ERR_KAFKA_SEEK_NOT_SUPPORTED} — the rejection reaches the caller instead of being
 * swallowed by the consume thread (MSG-F-02 seek facet).
 */
public class KafkaConsumeTask {
    static final Logger LOG = LoggerFactory.getLogger(KafkaConsumeTask.class);

    static final long DEFAULT_CONSUME_ERROR_BACKOFF_MS = 1000L;
    private static final long DEFAULT_POLL_TIMEOUT_MS = 1000L;

    private final KafkaMessageService service;
    private ExecutorService executor;
    final Consumer<String, String> consumer;
    private final MessageSubscriptionConfig config;
    private final long consumeErrorBackoffMs;
    private long pollTimeoutMs;

    private volatile boolean active = false;
    /**
     * WI20 (F-26): desired suspend state, set from the subscribing threads. The consume
     * thread re-asserts it every loop round (and after every partition assignment), which
     * serializes pause/resume with poll on the consumer's own thread — a cross-thread
     * {@code consumer.pause()} call would race the poll loop ({@code KafkaConsumer} is not
     * thread-safe). Survives rebalances and covers a suspend issued before any partition
     * was assigned.
     */
    private volatile boolean suspended = false;
    // Consume-thread confined: the last suspend state actually applied to the consumer.
    private boolean suspendedApplied = false;
    // Rebalance listener runs only on the poll thread; plain boolean suffices.
    private boolean seekToTimeApplied = false;
    // WI16: message-level failure context, filled by the inner catches before rethrow.
    private String lastFailureTopic;
    private String lastFailureLocation;
    private Object lastFailureMessage;
    private final java.util.Map<String, Integer> failureAttempts = new java.util.HashMap<>();


    /** WI7: created before the executor exists; bindExecutor completes the wiring. */
    KafkaConsumeTask(KafkaMessageService service, Consumer<String, String> consumer,
                     MessageSubscriptionConfig config, long consumeErrorBackoffMs) {
        this.service = service;
        this.consumer = consumer;
        this.config = config;
        this.consumeErrorBackoffMs = consumeErrorBackoffMs;
        this.pollTimeoutMs = DEFAULT_POLL_TIMEOUT_MS;
    }

    public KafkaConsumeTask(KafkaMessageService service, ExecutorService executor,
                            Consumer<String, String> consumer,
                            MessageSubscriptionConfig config,
                            long consumeErrorBackoffMs, long pollTimeoutMs) {
        this.service = service;
        this.executor = executor;
        this.consumer = consumer;
        this.config = config;
        this.consumeErrorBackoffMs = consumeErrorBackoffMs;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * Binds the consume executor and poll timeout when the task is constructed before the
     * executor exists (WI7: the task is created ahead of {@code consumer.subscribe} so the
     * rebalance listener can be registered with the subscription).
     */
    void bindExecutor(ExecutorService executor, long pollTimeoutMs) {
        this.executor = executor;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * The consumer-group rebalance listener backing the subscription (WI7/D3): seek origins
     * are resolved per assignment. {@code onPartitionsRevoked} is intentionally empty —
     * offsets are committed synchronously after every processed batch, so there is nothing
     * to do on revocation; it is declared explicitly rather than left as a silent stub.
     */
    org.apache.kafka.clients.consumer.ConsumerRebalanceListener rebalanceListener() {
        return new org.apache.kafka.clients.consumer.ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // Intentional no-op: commits already happened after each processed batch.
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                KafkaConsumeTask.this.onPartitionsAssigned(partitions);
            }
        };
    }

    public void start() {
        active = true;
        executor.execute(this::runTask);
    }

    /** Test seam: drives the poll loop on the caller thread (WI7 poll-first assertions). */
    void runTaskForTest() {
        active = true;
        runTask();
    }

    /**
     * D3 seek-origin resolution, invoked from the consumer group's rebalance callback for
     * the freshly assigned partitions. Runs on the poll (consume executor) thread only.
     *
     * <p>Gate: INIT_SEEK modes apply only when the consumer group has no committed offset
     * for the partition — a restart must resume from the committed position, not rewind
     * the whole log. ALWAYS_SEEK_TO_END re-applies on every assignment. seekToTime applies
     * once per task instance (replaying it on every rebalance would repeatedly rewind to
     * the same timestamp). A seek failure propagates out of {@code poll()} into the
     * consume loop's backoff handling, and the next assignment retries it.
     */
    void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }
        // WI20 (F-26): a suspend issued before this assignment (or before a rebalance) must
        // hold for the freshly assigned partitions too.
        if (suspended) {
            try {
                consumer.pause(partitions);
                suspendedApplied = true;
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.suspend-apply-fail", e);
            }
        }
        io.nop.api.core.message.MessageSubscribeOptions options = config.getOptions();

        SeekMode mode = options == null ? null : options.getSeekMode();
        if (mode == SeekMode.ALWAYS_SEEK_TO_END) {
            consumer.seekToEnd(partitions);
        } else if (mode == SeekMode.INIT_SEEK_TO_BEGIN || mode == SeekMode.INIT_SEEK_TO_END) {
            // D3 gate: only partitions the group has NEVER committed — a restart resumes
            // from the committed offset instead of rewinding the whole log.
            Set<TopicPartition> uncommitted = new HashSet<>();
            for (TopicPartition tp : partitions) {
                if (consumer.committed(Collections.singleton(tp)).get(tp) == null) {
                    uncommitted.add(tp);
                }
            }
            if (!uncommitted.isEmpty()) {
                if (mode == SeekMode.INIT_SEEK_TO_BEGIN) {
                    consumer.seekToBeginning(uncommitted);
                } else {
                    consumer.seekToEnd(uncommitted);
                }
            }
        }

        if (options != null && options.getSeekToTime() > 0 && !seekToTimeApplied) {
            seekToTimeApplied = true;
            seekByTime(options.getSeekToTime());
        }
    }

    private void seekByTime(long timestamp) {
        Map<TopicPartition, Long> timestamps = new HashMap<>();
        for (TopicPartition partition : consumer.assignment()) {
            timestamps.put(partition, timestamp);
        }
        Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> offsets =
                consumer.offsetsForTimes(timestamps);
        if (offsets == null) {
            return;
        }
        for (Map.Entry<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> entry : offsets.entrySet()) {
            if (entry.getValue() != null) {
                consumer.seek(entry.getKey(), entry.getValue().offset());
            }
        }
    }

    public void stop() {
        active = false;
        consumer.wakeup();
    }

    /**
     * WI20 (F-26): sets the desired suspend state. The consume thread applies pause/resume
     * at the top of the next poll round (and re-applies pause to freshly assigned
     * partitions in the rebalance callback) — never from the caller's thread, which would
     * race the poll loop. Applying is best-effort: if the task has already stopped, the
     * flag is recorded but no state is applied.
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isSuspended() {
        return suspended;
    }

    /**
     * Consume-thread only: re-asserts the desired suspend state against the full current
     * assignment. Diff-based (desired vs applied) so a suspend→resume pair issued between
     * two rounds is honored correctly and repeated rounds are no-ops.
     */
    private void applySuspendState() {
        if (suspended == suspendedApplied) {
            return;
        }
        suspendedApplied = suspended;
        try {
            if (suspended) {
                consumer.pause(consumer.assignment());
            } else {
                consumer.resume(consumer.assignment());
            }
        } catch (Exception e) {
            LOG.error("nop.err.message.kafka.suspend-apply-fail", e);
        }
    }

    public boolean isActive() {
        return active;
    }

    private void runTask() {
        while (active) {
            // WI20 (F-26): suspend/resume are applied HERE — on the consume thread, between
            // rounds — never from the subscribing thread.
            applySuspendState();
            try {
                if (config.getOptions() != null && config.getOptions().allowBatchConsume()) {
                    batchConsume();
                } else {
                    pollAndConsume();
                }
            } catch (WakeupException we) {
                // stop() woke the consumer — exit gracefully.
                break;
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.consume-fail", e);
                dispatchConsumeFailure(e);
                if (active) {
                    try {
                        Thread.sleep(consumeErrorBackoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Single-record consume path: polls, then for each record invokes the consumer and
     * dispatches the 5 return-value semantics individually (commit per record group).
     */
    void pollAndConsume() throws Exception {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        if (records == null || records.isEmpty()) {
            return;
        }

        IMessageConsumer consumerFn = config.getConsumer();

        for (ConsumerRecord<String, String> record : records) {
            ApiMessage apiMessage = KafkaHelper.buildApiMessage(record.topic(), record.partition(),
                    record.offset(), record.timestamp(), record.key(), record.value(), record.headers());

            ConsumeContext context = newConsumeContext();
            try {
                Object response = consumerFn.onMessage(record.topic(), apiMessage, context);
                response = FutureHelper.getResult(response);

                if (response instanceof ConsumeLater) {
                    // MSG-F-09 (WI12): a deferring response is a batch endpoint, mirroring
                    // the batch path (WI11). The partition commit boundary is monotonic —
                    // committing "skip this record" is impossible, so records after the
                    // deferral would be re-delivered anyway; dispatching them here only
                    // duplicates side effects. Honor the requested delay (the loop sleeps
                    // between polls) instead of re-polling immediately (CPU spin).
                    consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                    deferAfterBatch(((ConsumeLater) response).getDelay());
                    return;
                } else if (response instanceof TerminateConsume) {
                    // D5(b-prime): poison message is acknowledged (committed), never
                    // redelivered, and never dispatched to the ack topic.
                    LOG.warn("nop.message.kafka.terminate-consume:topic={},offset={},reason={}",
                            record.topic(), record.offset(), ((TerminateConsume) response).getReason());
                } else if (response instanceof PartialCommit) {
                    // Single-record dispatch has no batch boundary; treated as completion
                    // (declared in the WI6 plan). Full batch semantics live in batchConsume.
                } else if (response instanceof Acknowledge) {
                    // Acknowledge: send reply to ack topic, then commit.
                    context.send(service.getAckTopic(record.topic()),
                            ((Acknowledge) response).getReplyMessage());
                } else {
                    if (response != null) {
                        context.send(service.getAckTopic(record.topic()), response);
                    }
                }
                context.commit();
                // WI16: success resets the consecutive-failure counter for this location.
                failureAttempts.remove(record.topic() + "-" + record.partition() + "@" // invariant-ok: nop-message/18-wi18-gate-rules.md (intentional counting key; WI16)
                        + record.offset());
            } catch (Exception e) {
                LOG.error("nop.err.message.kafka.consume-record-fail:topic={},offset={}",
                        record.topic(), record.offset(), e);
                context.rollback();
                // WI16: carry the record context up to the run loop's failure dispatch.
                lastFailureTopic = record.topic();
                lastFailureLocation = record.topic() + "-" + record.partition() + "@" + record.offset();
                lastFailureMessage = apiMessage;
                // Re-seek to the failed record for at-least-once redelivery.
                consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
                throw e;
            }
        }

        try {
            consumer.commitSync();
        } catch (Exception e) {
            LOG.error("nop.err.message.kafka.commit-fail", e);
        }
    }

    /**
     * Sleeps for the caller-requested redelivery delay (MSG-F-09) so the deferring record
     * is not re-polled immediately (100% CPU spin). Sliced at 500ms and responsive to
     * thread interrupt: the cancel path's {@code shutdownNow()} interrupts the sleep, so
     * a long delay does not delay cancellation — {@code wakeup()} alone does not interrupt
     * {@code Thread.sleep}.
     *
     * <p>No cap: the caller's delay is honored as requested; capping/backoff policy belongs
     * to WI22 and the {@code max.poll.interval.ms} configuration surface to WI17. If the
     * delay exceeds the poll interval budget, the group kicks and rebalances (affecting all
     * members) and the subsequent commit fails into the run loop's error handling — the
     * redelivery can only ever be LATER than requested, never earlier.
     */
    private void deferAfterBatch(long delayMs) {
        long remaining = Math.max(delayMs, 0L);
        while (remaining > 0) {
            long slice = Math.min(remaining, 500L);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            remaining -= slice;
        }
    }

    /**
     * Batch consume path (when {@code batchReceiveCount > 0}): polls, builds a
     * {@link TopicMessage} list, invokes {@link IMessageConsumer#onMessageBatch}, then
     * dispatches per-record return semantics (mirrors PulsarConsumeTask.batchConsume).
     */
    /**
     * Batch consume path (WI11 boundary semantics).
     *
     * <p><strong>Commit boundary (F-07).</strong> Commits are partition-converged to the
     * highest finalized record per partition ({@code offset+1}) — a no-arg commitSync would
     * also commit partitions whose records were never dispatched (at-most-once loss).
     * Partitions whose convergence boundary falls short of the batch tail are seeked back
     * to the first un-dispatched offset so the records are re-polled in-process instead of
     * waiting for a rebalance.
     *
     * <p><strong>ConsumeLater (F-08).</strong> A deferring response is a batch endpoint:
     * dispatch stops there, the deferring record is re-seeked and nothing is committed.
     * This eliminates the dispatch/reply of records after the break point (they would be
     * re-delivered anyway). Records replied BEFORE the break point may still be re-delivered
     * and re-replied after a restart or rebalance — the unavoidable at-least-once residue.
     * Invariant: ConsumeLater ⇒ nothing in the batch is committed.
     *
     * <p><strong>Failure rewind (F-06).</strong> An exception rewinds each affected
     * partition to its first not-finalized record of this batch (progress pointer), so the
     * batch is re-polled instead of silently skipped. A null response list from the handler
     * is treated as a full-batch failure and retried (mirrors the Pulsar batch negativeAck).
     *
     * <p>A PartialCommit response keeps its WI6 prefix-commit semantics. If the boundary
     * commitSync itself throws, the failure falls into the rewind path below (already
     * committed records may be re-delivered — safe, never lost).
     */
    void batchConsume() throws Exception {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
        if (records == null || records.isEmpty()) {
            return;
        }

        List<TopicMessage> list = new ArrayList<>(records.count());
        List<ConsumerRecord<String, String>> recordList = new ArrayList<>(records.count());
        for (ConsumerRecord<String, String> record : records) {
            ApiMessage apiMessage = KafkaHelper.buildApiMessage(record.topic(), record.partition(),
                    record.offset(), record.timestamp(), record.key(), record.value(), record.headers());
            list.add(new TopicMessage(record.topic(), apiMessage));
            recordList.add(record);
        }

        IMessageConsumer consumerFn = config.getConsumer();
        ConsumeContext ctx = newConsumeContext();
        int progress = 0;
        try {
            List<Object> responses = consumerFn.onMessageBatch(list, ctx);
            if (responses == null) {
                throw new IllegalStateException("onMessageBatch returned null");
            }
            Map<TopicPartition, Long> committedBoundaries = new HashMap<>();
            for (int i = 0; i < responses.size() && i < recordList.size(); i++) {
                progress = i;
                Object response = FutureHelper.getResult(responses.get(i));
                ConsumerRecord<String, String> record = recordList.get(i);
                if (response instanceof PartialCommit) {
                    // WI6 prefix-commit semantics: records [0..k-1] only, then stop.
                    commitConverged(boundariesAfter(committedBoundaries, recordList, i));
                    return;
                } else if (response instanceof TerminateConsume) {
                    LOG.warn("nop.message.kafka.terminate-consume:topic={},offset={},reason={}",
                            record.topic(), record.offset(), ((TerminateConsume) response).getReason());
                } else if (response instanceof ConsumeLater) {
                    // F-08 batch endpoint: nothing after this record is dispatched, nothing
                    // in the batch is committed; re-seek so the deferring record redelivers.
                    anyConsumeLaterSeek(record);
                    // WI12: honor the delay (see deferAfterBatch) instead of spinning.
                    deferAfterBatch(((ConsumeLater) response).getDelay());
                    return;
                } else if (response instanceof Acknowledge) {
                    ctx.send(service.getAckTopic(record.topic()),
                            ((Acknowledge) response).getReplyMessage());
                } else if (response != null) {
                    ctx.send(service.getAckTopic(record.topic()), response);
                }
                committedBoundaries.merge(new TopicPartition(record.topic(), record.partition()),
                        record.offset() + 1, Math::max);
            }
            ctx.commit();
            commitConverged(committedBoundaries);
            rewindUndispatched(recordList, committedBoundaries);
        } catch (Exception e) {
            // WI16: best-effort failure context from the progress pointer.
            if (progress < recordList.size()) {
                ConsumerRecord<String, String> failed = recordList.get(progress);
                lastFailureTopic = failed.topic();
                lastFailureLocation = failed.topic() + "-" + failed.partition() + "@"
                        + failed.offset();
                lastFailureMessage = list.get(progress).getMessage();
            }
            rewindFrom(recordList, progress);
            try {
                ctx.rollback();
            } catch (Exception rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
    }

    /**
     * WI16: dispatches {@link IMessageConsumer#onConsumeFailure} with the context captured
     * by the inner catches (framework-level failures get topic=null, attempt=0). The
     * dispatch is guarded — a throwing user callback must not kill the consume thread.
     */
    private void dispatchConsumeFailure(Exception e) {
        String location = lastFailureLocation;
        Object message = lastFailureMessage;
        lastFailureLocation = null;
        lastFailureMessage = null;
        int attempt = 0;
        if (location != null) {
            attempt = failureAttempts.merge(location, 1, Integer::sum);
        }
        try {
            config.getConsumer().onConsumeFailure(new io.nop.api.core.message.ConsumeFailure(
                    lastFailureTopic, location, message, attempt, e));
        } catch (Exception callbackError) {
            LOG.warn("nop.err.message.kafka.failure-callback-throw", callbackError);
        }
    }

    private void anyConsumeLaterSeek(ConsumerRecord<String, String> record) {
        consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());
    }

    private Map<TopicPartition, Long> boundariesAfter(Map<TopicPartition, Long> boundaries,
                                                      List<ConsumerRecord<String, String>> recordList,
                                                      int upto) {
        Map<TopicPartition, Long> result = new HashMap<>(boundaries);
        for (int j = 0; j < upto && j < recordList.size(); j++) {
            ConsumerRecord<String, String> record = recordList.get(j);
            result.merge(new TopicPartition(record.topic(), record.partition()),
                    record.offset() + 1, Math::max);
        }
        return result;
    }

    private void commitConverged(Map<TopicPartition, Long> boundaries) throws Exception {
        if (boundaries.isEmpty()) {
            return;
        }
        Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> toCommit =
                new HashMap<>();
        for (Map.Entry<TopicPartition, Long> entry : boundaries.entrySet()) {
            toCommit.put(entry.getKey(),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(entry.getValue()));
        }
        // Commit failure => not committed => redelivery; safe to surface as an exception
        // (the success-path call site treats it via the rewind catch).
        consumer.commitSync(toCommit);
    }

    /** Seeks partitions whose convergence boundary is short of the batch tail (F-07). */
    private void rewindUndispatched(List<ConsumerRecord<String, String>> recordList,
                                    Map<TopicPartition, Long> boundaries) {
        Map<TopicPartition, Long> firstUndispatched = new HashMap<>();
        for (ConsumerRecord<String, String> record : recordList) {
            TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            // A record is un-dispatched when no boundary covers it (its partition had no
            // finalized record) or it sits at/after the committed next-offset.
            Long boundary = boundaries.get(tp);
            if (boundary == null || record.offset() >= boundary) {
                firstUndispatched.merge(tp, record.offset(), Math::min);
            }
        }
        for (Map.Entry<TopicPartition, Long> entry : firstUndispatched.entrySet()) {
            consumer.seek(entry.getKey(), entry.getValue());
        }
    }

    /** Rewinds partitions to the first not-finalized record from the given progress (F-06). */
    private void rewindFrom(List<ConsumerRecord<String, String>> recordList, int progress) {
        Map<TopicPartition, Long> firstIncomplete = new HashMap<>();
        for (int i = progress; i < recordList.size(); i++) {
            ConsumerRecord<String, String> record = recordList.get(i);
            firstIncomplete.merge(new TopicPartition(record.topic(), record.partition()),
                    record.offset(), Math::min);
        }
        for (Map.Entry<TopicPartition, Long> entry : firstIncomplete.entrySet()) {
            consumer.seek(entry.getKey(), entry.getValue());
        }
    }

    ConsumeContext newConsumeContext() {
        return new ConsumeContext();
    }

    /**
     * {@link IMessageConsumeContext} implementation. Kafka has no native transactions in
     * the at-least-once first version (Non-Goal), so commit/rollback are no-ops and the
     * context only forwards sends through the enclosing {@link KafkaMessageService}
     * producer.
     */
    protected class ConsumeContext implements IMessageConsumeContext {
        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return service.sendAsync(topic, message, options);
        }

        public void commit() {
            // Per-record semantics handled by the enclosing task; nothing to do here.
        }

        public void rollback() {
            // Per-record semantics handled by the enclosing task; nothing to do here.
        }
    }
}
