/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.connector;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.connector.kafka.KafkaSourceFunction;
import io.nop.stream.connector.kafka.KafkaTwoPhaseCommitSink;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WI3 (plan 2335): the kill-recover zero-duplicate proof over a REAL broker —
 * source offsets in the checkpoint + commit-at-barrier transactional sink.
 *
 * <p>Scenario: 100 records flow source→sink; round 1 fails mid-run (injected, AFTER at
 * least one checkpoint is durable — the kill point sits OUTSIDE the documented residual
 * window [barrier commit, checkpoint durable)); round 2 resubmits the SAME jobId against
 * the SAME explicit storage path (checkpoint auto-restore: source seeks to the
 * checkpointed offsets, the dangling transaction is aborted by initTransactions) and
 * drains the rest. The committed view of the output topic must contain each key exactly
 * once.
 *
 * <p><b>Plan-pinned E2E preconditions</b>: explicit storage path (auto-restore only
 * fires with one), synchronous snapshots, both submissions through the SAME entry shape
 * ({@code executeWithCheckpoint(JobGraph...)}), and the assertion consumer runs
 * {@code isolation.level=read_committed} from {@code earliest}. The job ends via
 * {@code truncateForDrain()} — the terminal batch is committed by the last periodic
 * checkpoint's saveState (the terminal-assembly final checkpoint does not pass barriers
 * to a drained sink).
 *
 * <p><b>Gating semantics (KafkaBrokerE2ETest convention)</b>: primary gate = Docker
 * reachability (assume-skip, visibly); opt-out {@code -Dnop.test.stream.e2e.kafka=false};
 * image registry prefix {@code -Dnop.test.stream.e2e.registry=<prefix>} (default
 * {@code docker.m.daocloud.io/}).
 */
class TestKafkaConnectorExactlyOnceE2E {

    private static final int RECORD_COUNT = 100;
    private static final int KILL_AT = 60;

    private static final boolean DOCKER_AVAILABLE = detectDocker();
    private static final boolean OPTED_OUT =
            "false".equalsIgnoreCase(System.getProperty("nop.test.stream.e2e.kafka", "true"));
    private static final String REGISTRY_PREFIX =
            System.getProperty("nop.test.stream.e2e.registry", "docker.m.daocloud.io/");

    private static KafkaContainer kafka;

    /**
     * Bounded Docker probe: {@link DockerClientFactory#isDockerAvailable()} has no read
     * timeout and blocks forever on a half-hung docker socket (observed on Docker Desktop
     * with a stale socket file), which would wedge the whole build from a test-class
     * static initializer. Run it on a daemon thread and give up after 15s; the
     * shutdownNow() interrupt breaks the NIO blocking read.
     */
    static boolean detectDocker() {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "docker-detect");
            t.setDaemon(true);
            return t;
        });
        try {
            return executor.submit(() -> {
                try {
                    return DockerClientFactory.instance().isDockerAvailable();
                } catch (Throwable e) {
                    return false;
                }
            }).get(15, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    @AfterAll
    static void stopBroker() {
        if (kafka != null) {
            kafka.stop();
            kafka = null;
        }
    }

    private static void startBroker() {
        kafka = new KafkaContainer(
                DockerImageName.parse(REGISTRY_PREFIX + "apache/kafka:4.3.1")
                        .asCompatibleSubstituteFor("apache/kafka"));
        kafka.start();
    }

    @Test
    void killRecoverDeliversEachRecordExactlyOnce() throws Exception {
        assumeTrue(DOCKER_AVAILABLE && !OPTED_OUT,
                "docker unavailable or e2e opted out: real-broker kill-recover test gated off "
                        + "(SKIP is visible by design; the WI3 done verdict rests on an actual green run)");
        startBroker();
        String bootstrap = kafka.getBootstrapServers();

        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", bootstrap,
                "request.timeout.ms", "10000"))) {
            admin.createTopics(List.of(new NewTopic("in-topic", 1, (short) 1),
                    new NewTopic("out-topic", 1, (short) 1))).all().get(30, TimeUnit.SECONDS);
        }

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        Path storageDir = Files.createTempDirectory("wi3-kafka-e2e");
        CountDownLatch manifestReady = new CountDownLatch(1);

        // ============ round 1: run until the injected kill ============
        AtomicInteger consumed1 = new AtomicInteger();
        KafkaSourceFunction<String> source1 = new KafkaSourceFunction<>(bootstrap, "in-topic", value -> {
            if (consumed1.incrementAndGet() >= KILL_AT) {
                // belt-and-braces: the test only seeds record #60 after a durable
                // checkpoint artifact exists, so this await never actually blocks —
                // the kill point sits OUTSIDE the residual duplicate window
                try {
                    manifestReady.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StreamException("interrupted before the injected kill", e);
                }
                throw new StreamException("injected kill at record " + KILL_AT);
            }
            return new String(value);
        });
        KafkaTwoPhaseCommitSink<String> sink1 = new KafkaTwoPhaseCommitSink<String>(bootstrap, "out-topic",
                "wi3-e2e", (String v) -> v.getBytes(), (String v) -> v);

        AtomicReference<Throwable> failure1 = new AtomicReference<>();
        Thread t1 = runJob("kafka-e2e", source1, sink1, storageDir, failure1);

        // two-phase seeding: batch 1 (records 0..KILL_AT-2) flows and gets checkpointed
        // while the source idles between polls; only after a durable checkpoint artifact
        // exists do we seed record #60 — the injected kill — and the rest
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < KILL_AT - 1; i++) {
                producer.send(new ProducerRecord<>("in-topic", key(i), "v" + i)).get();
            }
            producer.flush();

            awaitManifest(storageDir);
            manifestReady.countDown();

            producer.send(new ProducerRecord<>("in-topic", key(KILL_AT - 1), "v" + (KILL_AT - 1))).get();
            producer.flush();
        }
        t1.join(120_000);
        assertNotNull(failure1.get(), "round 1 must fail at the injected kill; failure=" + failure1.get());

        // ============ round 2: same jobId + same storage path = auto-restore ============
        AtomicInteger consumed2 = new AtomicInteger();
        KafkaSourceFunction<String> source2 = new KafkaSourceFunction<>(bootstrap, "in-topic", value -> {
            consumed2.incrementAndGet();
            return new String(value);
        });
        KafkaTwoPhaseCommitSink<String> sink2 = new KafkaTwoPhaseCommitSink<String>(bootstrap, "out-topic",
                "wi3-e2e", (String v) -> v.getBytes(), (String v) -> v);

        AtomicReference<Throwable> failure2 = new AtomicReference<>();
        Thread t2 = runJob("kafka-e2e", source2, sink2, storageDir, failure2);

        // barrier mails drain only at the source's collect() emission point — an idle
        // source never processes checkpoint triggers (engine semantic, see WI10's
        // terminal-fire discovery). Keep records FLOWING so periodic checkpoints fire
        // mid-flow and the commit-at-barrier epochs carry every record.
        Thread seeder = new Thread(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
                for (int i = KILL_AT; i < RECORD_COUNT; i++) {
                    try {
                        producer.send(new ProducerRecord<>("in-topic", key(i), "v" + i)).get();
                        Thread.sleep(150L);
                    } catch (Exception e) {
                        throw new StreamException("seeder failed", e);
                    }
                }
                // tail-carry fillers: after the last seeded record the source goes idle
                // and no further barrier fires, so the tail epoch would never commit.
                // Keep the flow alive ("v-fill-*" records are outside the asserted key
                // space) until every asserted record is committed.
                for (int f = 0; f < 40; f++) {
                    try {
                        producer.send(new ProducerRecord<>("in-topic", "fill-" + f,
                                "v-fill-" + f)).get();
                        Thread.sleep(150L);
                    } catch (Exception e) {
                        throw new StreamException("seeder failed", e);
                    }
                }
                producer.flush();
            } catch (Exception e) {
                throw new StreamException("seeder failed", e);
            }
        }, "wi3-e2e-seeder");
        seeder.start();

        // ============ assertion: read_committed view has each key exactly once ============
        List<String> seen = new CopyOnWriteArrayList<>();
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "wi3-e2e-verifier-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("out-topic"));
            long deadline = System.currentTimeMillis() + 120_000L;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(300L))) {
                    seen.add(r.key());
                }
                // count only the asserted key space ("v%03d"); tail-carry fillers are
                // "v-fill-*" and must not end the wait early
                long distinct = seen.stream().filter(k -> k.startsWith("v")
                        && !k.startsWith("v-fill-")).distinct().count();
                if (distinct >= RECORD_COUNT) {
                    break;
                }
            }
        }

        Map<String, Long> counts = new HashMap<>();
        for (String k : seen) {
            counts.merge(k, 1L, Long::sum);
        }
        List<String> missing = new ArrayList<>();
        List<String> duplicated = new ArrayList<>();
        for (int i = 0; i < RECORD_COUNT; i++) {
            // the pipeline carries the record VALUE and the sink keys by it, so the
            // committed out-topic key space is "v%03d"
            String committedKey = "v" + i;
            Long n = counts.get(committedKey);
            if (n == null) {
                missing.add(committedKey);
            } else if (n != 1L) {
                duplicated.add(committedKey + "x" + n);
            }
        }
        if (!missing.isEmpty() || !duplicated.isEmpty()) {
            fail("missing=" + missing + ", duplicated=" + duplicated
                    + " (total seen=" + seen.size() + ", distinct=" + counts.size() + ")");
        }
        long committedKeys = counts.keySet().stream()
                .filter(k -> k.startsWith("v") && !k.startsWith("v-fill-")).count();
        assertEquals(RECORD_COUNT, committedKeys, "every seeded key must appear in the committed view");
        assertTrue(consumed2.get() > 0, "round 2 must actually replay post-checkpoint records");

        // drain to a natural terminal state so execute() returns (the post-run
        // drainControlMails processes any pending checkpoint trigger mail)
        source2.truncateForDrain();
        t2.join(60_000);
        assertEquals(null, failure2.get(), "round 2 must complete without failure: " + failure2.get());
        seeder.join(30_000);
    }

    private static String key(int i) {
        return String.format(Locale.ROOT, "k%03d", i);
    }

    private static Thread runJob(String jobId, SourceFunction<String> source, KafkaTwoPhaseCommitSink<String> sink,
                                 Path storageDir, AtomicReference<Throwable> failure) {
        Thread t = new Thread(() -> {
            try {
                StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
                StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sink);
                io.nop.stream.core.jobgraph.OperatorChain chain =
                        new io.nop.stream.core.jobgraph.OperatorChain(List.of(sourceOp, sinkOp));
                StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
                JobVertex vertex = new JobVertex("v1", "Chain", 1, List.of(chain), invokable);
                JobGraph jobGraph = new JobGraph(jobId);
                jobGraph.addVertex(vertex);

                CheckpointConfig config = new CheckpointConfig();
                config.setJobId(jobId);
                config.setPipelineId("pipeline-0");
                config.setCheckpointEnabled(true);
                config.setCheckpointInterval(300L);
                config.setCheckpointTimeout(30_000L);
                config.setAsyncSnapshotEnabled(false);
                config.setStorageType("local");
                config.setStorageProperty("path", storageDir.toString());

                GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, jobId, config);
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "wi3-e2e-" + jobId);
        t.start();
        return t;
    }

    /** Polls the storage dir until at least one checkpoint artifact exists. */
    private static void awaitManifest(Path storageDir) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(storageDir)) {
                try (var walk = Files.walk(storageDir)) {
                    long files = walk.filter(Files::isRegularFile).count();
                    if (files > 0) {
                        return;
                    }
                }
            }
            Thread.sleep(100L);
        }
        fail("no checkpoint artifact appeared within 60s under " + storageDir);
    }
}
