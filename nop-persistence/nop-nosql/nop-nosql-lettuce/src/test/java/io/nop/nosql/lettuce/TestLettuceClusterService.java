/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce;

import io.nop.nosql.core.INosqlCounter;
import io.nop.nosql.core.INosqlHashOperations;
import io.nop.nosql.core.INosqlLock;
import io.nop.nosql.core.INosqlQueue;
import io.nop.nosql.core.INosqlRanking;
import io.nop.nosql.core.INosqlSetOperations;
import io.nop.nosql.core.RankingEntry;
import io.nop.nosql.core.config.RedisConfig;
import io.nop.nosql.lettuce.impl.LettuceMessageService;
import io.nop.nosql.lettuce.impl.LettuceRedisConnectionProvider;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestLettuceClusterService {

    private static final int NUM_MASTERS = 1;
    private static final int NUM_REPLICAS = 0;
    private static final int CLUSTER_PORT = 6379;
    private static final int CLUSTER_BUS_PORT = 16379;

    private Network network;
    private List<GenericContainer<?>> nodes;
    private LettuceMessageService service;
    private LettuceRedisConnectionProvider provider;

    @BeforeAll
    @SuppressWarnings("resource")
    void startCluster() throws Exception {
        network = Network.newNetwork();
        nodes = new ArrayList<>();

        for (int i = 0; i < NUM_MASTERS + NUM_REPLICAS; i++) {
            GenericContainer<?> node = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withNetwork(network)
                    .withNetworkAliases("redis-node-" + i)
                    .withExposedPorts(CLUSTER_PORT, CLUSTER_BUS_PORT)
                    .withCommand(
                            "redis-server",
                            "--port", String.valueOf(CLUSTER_PORT),
                            "--cluster-enabled", "yes",
                            "--cluster-config-file", "nodes.conf",
                            "--cluster-node-timeout", "10000",
                            "--appendonly", "yes"
                    );
            node.start();

            int mappedPort = node.getMappedPort(CLUSTER_PORT);
            int mappedBusPort = node.getMappedPort(CLUSTER_BUS_PORT);

            // Single-node cluster: announce 127.0.0.1 so host-side Lettuce can connect
            node.execInContainer("redis-cli", "CONFIG", "SET", "cluster-announce-ip", "127.0.0.1");
            node.execInContainer("redis-cli", "CONFIG", "SET", "cluster-announce-port", String.valueOf(mappedPort));
            node.execInContainer("redis-cli", "CONFIG", "SET", "cluster-announce-bus-port", String.valueOf(mappedBusPort));

            nodes.add(node);
        }

        if (nodes.size() == 1) {
            GenericContainer<?> sole = nodes.get(0);
            sole.execInContainer("sh", "-c",
                    "redis-cli cluster addslots $(seq 0 16383)");
            sole.execInContainer("redis-cli", "cluster", "meet",
                    "127.0.0.1", String.valueOf(CLUSTER_PORT));
            Thread.sleep(5000);
            sole.execInContainer("redis-cli", "cluster", "set-config-epoch", "1");
        } else {
            StringBuilder clusterCreateCmd = new StringBuilder();
            clusterCreateCmd.append("redis-cli --cluster create");
            for (int i = 0; i < nodes.size(); i++) {
                clusterCreateCmd.append(" redis-node-").append(i).append(":").append(CLUSTER_PORT);
            }
            if (NUM_REPLICAS > 0) {
                clusterCreateCmd.append(" --cluster-replicas 1");
            }
            clusterCreateCmd.append(" --cluster-yes");

            GenericContainer<?> firstNode = nodes.get(0);
            String result = firstNode.execInContainer("sh", "-c", clusterCreateCmd.toString()).getStdout();
            if (!result.contains("[OK]")) {
                throw new RuntimeException("Failed to create Redis cluster: " + result);
            }
        }

        Thread.sleep(5000);

        List<String> clusterNodeUris = new ArrayList<>();
        for (int i = 0; i < NUM_MASTERS; i++) {
            clusterNodeUris.add("127.0.0.1:" + nodes.get(i).getMappedPort(CLUSTER_PORT));
        }

        RedisConfig config = new RedisConfig();
        config.setClusterNodes(clusterNodeUris);
        config.setConnectionTimeout(10000);
        config.setSoTimeout(10000);

        provider = new LettuceRedisConnectionProvider();
        provider.setConfig(config);
        provider.start();

        service = new LettuceMessageService(provider);
    }

    @AfterAll
    void stopCluster() {
        if (provider != null) {
            provider.stop();
        }
        if (nodes != null) {
            for (GenericContainer<?> node : nodes) {
                node.stop();
            }
        }
        if (network != null) {
            network.close();
        }
    }

    private void cleanupKeys(String... keys) {
        for (String key : keys) {
            service.remove(key);
        }
    }

    @Test
    @Order(1)
    void testClusterBasicOperations() {
        String key = "cluster:test:basic";

        service.put(key, "hello");
        assertEquals("hello", String.valueOf(service.get(key)));

        INosqlCounter counter = service.counter("cluster:test:counter");
        counter.increment(5);
        assertEquals(5, counter.get());
        counter.increment(-2);
        assertEquals(3, counter.get());

        INosqlQueue queue = service.queue("cluster:test:queue");
        queue.enqueue("a");
        queue.enqueue("b");
        assertEquals("a", String.valueOf(queue.dequeue()));
        assertEquals("b", String.valueOf(queue.dequeue()));

        INosqlLock lock = service.lock("cluster:test:lock");
        assertTrue(lock.tryLock(5000));
        assertTrue(lock.isHeld());
        lock.unlock();
        assertFalse(lock.isHeld());

        cleanupKeys(key, "cluster:test:counter", "cluster:test:queue", "cluster:test:lock");
    }

    @Test
    @Order(2)
    void testClusterSlotRouting() {
        String key1 = "cluster:slot:a";
        String key2 = "cluster:slot:b";
        String key3 = "cluster:slot:c";

        service.put(key1, "value1");
        service.put(key2, "value2");
        service.put(key3, "value3");

        assertEquals("value1", String.valueOf(service.get(key1)));
        assertEquals("value2", String.valueOf(service.get(key2)));
        assertEquals("value3", String.valueOf(service.get(key3)));

        cleanupKeys(key1, key2, key3);
    }

    @Test
    @Order(3)
    void testClusterLockOnDifferentSlots() {
        INosqlLock lock1 = service.lock("cluster:lock:slotA");
        INosqlLock lock2 = service.lock("cluster:lock:slotB");

        assertTrue(lock1.tryLock(5000));
        assertTrue(lock1.isHeld());

        // lock2 is a different key on a different slot, should be independent
        assertTrue(lock2.tryLock(5000));
        assertTrue(lock2.isHeld());

        lock1.unlock();
        assertFalse(lock1.isHeld());
        assertTrue(lock2.isHeld());

        lock2.unlock();
        assertFalse(lock2.isHeld());

        cleanupKeys("cluster:lock:slotA", "cluster:lock:slotB");
    }

    @Test
    @Order(4)
    void testClusterCounterAcrossSlots() {
        INosqlCounter counter1 = service.counter("cluster:counter:alpha");
        INosqlCounter counter2 = service.counter("cluster:counter:beta");

        counter1.increment(10);
        counter2.increment(20);

        assertEquals(10, counter1.get());
        assertEquals(20, counter2.get());

        counter1.increment(5);
        assertEquals(15, counter1.get());
        assertEquals(20, counter2.get());

        cleanupKeys("cluster:counter:alpha", "cluster:counter:beta");
    }

    @Test
    @Order(5)
    void testClusterRankingAcrossSlots() {
        INosqlRanking ranking1 = service.ranking("cluster:rank:gameA");
        INosqlRanking ranking2 = service.ranking("cluster:rank:gameB");

        ranking1.add("player1", 100);
        ranking1.add("player2", 200);

        ranking2.add("playerX", 500);
        ranking2.add("playerY", 300);

        assertEquals(100.0, ranking1.getScore("player1"), 0.001);
        assertEquals(200.0, ranking1.getScore("player2"), 0.001);
        assertEquals(500.0, ranking2.getScore("playerX"), 0.001);

        List<RankingEntry> top1 = ranking1.getTopN(10);
        assertEquals(2, top1.size());
        assertEquals("player2", top1.get(0).getMember());

        List<RankingEntry> top2 = ranking2.getTopN(10);
        assertEquals(2, top2.size());
        assertEquals("playerX", top2.get(0).getMember());

        cleanupKeys("cluster:rank:gameA", "cluster:rank:gameB");
    }

    @Test
    @Order(6)
    void testClusterHashTagRouting() {
        // {tag} forces keys to the same hash slot for multi-key ops
        INosqlSetOperations setOps = service.setOps("cluster:{tag1}:set");
        setOps.add("member1");
        setOps.add("member2");
        setOps.add("member3");
        assertEquals(3, setOps.size());

        Set<Object> members = setOps.members();
        assertEquals(3, members.size());
        assertTrue(members.contains("member1"));
        assertTrue(members.contains("member2"));
        assertTrue(members.contains("member3"));

        INosqlHashOperations hashOps = service.hashOps("cluster:{tag1}:hash");
        hashOps.put("field1", "value1");
        hashOps.put("field2", "value2");

        assertEquals("value1", String.valueOf(hashOps.get("field1")));
        assertEquals("value2", String.valueOf(hashOps.get("field2")));

        Map<String, Object> allFields = hashOps.getAllAsync().toCompletableFuture().join();
        assertEquals(2, allFields.size());

        cleanupKeys("cluster:{tag1}:set", "cluster:{tag1}:hash");
    }

    @Test
    @Order(7)
    void testClusterMultipleConnections() {
        List<String> clusterNodes = new ArrayList<>();
        for (int i = 0; i < NUM_MASTERS; i++) {
            clusterNodes.add(nodes.get(i).getHost() + ":" + nodes.get(i).getMappedPort(CLUSTER_PORT));
        }

        RedisConfig config = new RedisConfig();
        config.setClusterNodes(clusterNodes);
        config.setConnectionPoolSize(3);
        config.setConnectionTimeout(5000);
        config.setSoTimeout(5000);

        LettuceRedisConnectionProvider poolProvider = new LettuceRedisConnectionProvider();
        poolProvider.setConfig(config);
        poolProvider.start();

        try {
            LettuceMessageService poolService = new LettuceMessageService(poolProvider);

            for (int i = 0; i < 10; i++) {
                String key = "cluster:pool:key" + i;
                poolService.put(key, "val" + i);
            }

            for (int i = 0; i < 10; i++) {
                String key = "cluster:pool:key" + i;
                assertEquals("val" + i, String.valueOf(poolService.get(key)));
                poolService.remove(key);
            }
        } finally {
            poolProvider.stop();
        }
    }
}
