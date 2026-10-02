package io.nop.cluster.elector;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 leader 选举的轮询语义：start 后经调度器触发 checkElection、
 * 选举完成回填 epoch 并通知 listener、restart 重开一轮选举、stop 清理 listener 并复位状态。
 */
@Timeout(10)
public class TestAbstractLeaderElector {

    static class RecordingListener implements ILeaderElectionListener {
        final List<LeaderEpoch> leaderEpochs = new CopyOnWriteArrayList<>();
        final List<LeaderEpoch> followerEpochs = new CopyOnWriteArrayList<>();
        final AtomicInteger stopCount = new AtomicInteger();

        @Override
        public void becomeLeader(LeaderEpoch leaderEpoch) {
            leaderEpochs.add(leaderEpoch);
        }

        @Override
        public void becomeFollower(LeaderEpoch leaderEpoch) {
            followerEpochs.add(leaderEpoch);
        }

        @Override
        public void onStop() {
            stopCount.incrementAndGet();
        }
    }

    static class TestElector extends AbstractPollingLeaderElector {
        final AtomicInteger electionCount = new AtomicInteger();

        @Override
        protected Void checkElection() {
            electionCount.incrementAndGet();
            return null;
        }

        @Override
        public void restartElection() {
            onRestartElection();
        }

        // 暴露 protected 方法供测试断言
        public void complete(LeaderEpoch epoch) {
            onElectionCompleted(epoch);
        }

        public void fireBecomeLeader(LeaderEpoch epoch) {
            onBecomeLeader(epoch);
        }

        public void fireBecomeFollower(LeaderEpoch epoch) {
            onBecomeFollower(epoch);
        }
    }

    static LeaderEpoch epoch(String leaderId, long epochNo) {
        return new LeaderEpoch(leaderId, epochNo, new Timestamp(System.currentTimeMillis() + 30_000));
    }

    private static final java.util.concurrent.atomic.AtomicInteger TIMER_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    private DefaultScheduledExecutor timer;
    private TestElector elector;

    @BeforeEach
    public void setUp() {
        // 每个用例独立命名 timer，避免 micrometer 同名 gauge 重复注册的跨用例串扰
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-elector-timer-" + TIMER_SEQ.incrementAndGet());
        elector = new TestElector();
        elector.setScheduledExecutor(timer);
    }

    @AfterEach
    public void tearDown() {
        elector.stop();
        timer.destroy();
    }

    @Test
    public void testStartTriggersPollingCheckElection() throws Exception {
        elector.start();
        // 自旋 + 短 sleep 等待调度器触发首轮 checkElection（防挂起规则）
        for (int i = 0; i < 200 && elector.electionCount.get() == 0; i++) {
            Thread.sleep(10);
        }
        assertTrue(elector.electionCount.get() >= 1,
                "start must schedule at least one checkElection run");
        assertTrue(elector.isActive(), "elector must be active after start");
    }

    @Test
    public void testElectionCompletionPublishesEpoch() throws Exception {
        LeaderEpoch e = epoch("node-1", 7);
        elector.complete(e);

        LeaderEpoch seen = elector.whenElectionCompleted()
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(e, seen, "election promise must publish the winning epoch");
        assertEquals("node-1", elector.getLeaderEpoch().getLeaderId());
        assertEquals(7, elector.getLeaderEpoch().getEpoch());
    }

    @Test
    public void testListenersNotifiedOnLeaderAndFollower() {
        RecordingListener listener = new RecordingListener();
        AutoCloseable handle = elector.addElectionListener(listener);
        LeaderEpoch e = epoch("node-1", 1);

        elector.fireBecomeLeader(e);
        elector.fireBecomeFollower(epoch("node-2", 2));

        assertEquals(List.of(e), listener.leaderEpochs, "becomeLeader must reach listener");
        assertEquals(1, listener.followerEpochs.size(), "becomeFollower must reach listener");
        assertEquals("node-2", listener.followerEpochs.get(0).getLeaderId());

        // 注销后不再收到通知
        try {
            handle.close();
        } catch (Exception ex) {
            throw NopException.adapt(ex);
        }
        elector.fireBecomeLeader(epoch("node-3", 3));
        assertEquals(1, listener.leaderEpochs.size(), "closed listener handle must unsubscribe");
    }

    @Test
    public void testStopNotifiesListenersAndResetsEpoch() throws Exception {
        elector.start();
        RecordingListener listener = new RecordingListener();
        elector.addElectionListener(listener);
        elector.complete(epoch("node-1", 1));
        // 显式超时等待选举 promise 完成
        elector.whenElectionCompleted().toCompletableFuture().get(5, TimeUnit.SECONDS);

        elector.stop();

        assertEquals(1, listener.stopCount.get(), "stop must fire listener.onStop once");
        assertNull(elector.getLeaderEpoch(), "stop must reset leader epoch");
    }

    @Test
    public void testRestartElectionResetsStateAndOpensNewPromise() throws Exception {
        elector.complete(epoch("node-1", 1));
        elector.whenElectionCompleted().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("node-1", elector.getLeaderEpoch().getLeaderId());

        elector.restartElection();

        assertNull(elector.getLeaderEpoch(), "restart must clear current epoch");
        // 新一轮选举的 promise 未完成
        assertEquals(false, elector.whenElectionCompleted().toCompletableFuture().isDone(),
                "restart must open a fresh, incomplete election promise");
    }

    @Test
    public void testHostIdDefaultsToConfiguredValue() {
        elector.setHostId("fixed-host");
        assertEquals("fixed-host", elector.getHostId());
    }
}
