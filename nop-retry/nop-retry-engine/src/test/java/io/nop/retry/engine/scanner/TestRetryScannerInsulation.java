package io.nop.retry.engine.scanner;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.retry.engine.store.IRetryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2496 1.6-C8-001 回归：扫描链接入平台周期任务绝缘层——任务体 Error 不再（近）静默杀死
 * 扫描器，链存活可重臂；scanner 存活/最后扫描时间可观测；设施级拒绝时 running 状态真实化。
 */
public class TestRetryScannerInsulation {

    private final List<RetryScannerImpl> liveScanners = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        for (RetryScannerImpl scanner : liveScanners) {
            try {
                scanner.stopScanning();
            } catch (Exception ignored) {
                // intentionally ignored: teardown best-effort stop; test already asserted chain behavior
            }
        }
        liveScanners.clear();
    }

    /**
     * 生产同构载体：globalTimer + BindScheduledExecutor（getExecutor() 的真实形态），
     * 任务体在同线程 runner 上执行（确定性）。
     */
    static class BindCarrierScanner extends RetryScannerImpl {
        @Override
        protected IScheduledExecutor getExecutor() {
            return GlobalExecutors.globalTimer().executeOn(Runnable::run);
        }
    }

    /**
     * 设施级拒绝载体：timer 正常，worker 提交一律被拒（提交拒绝 = 链真死且不复活）。
     */
    static class RejectingCarrierScanner extends RetryScannerImpl {
        @Override
        protected IScheduledExecutor getExecutor() {
            return GlobalExecutors.globalTimer().executeOn(command -> {
                throw new RejectedExecutionException("worker-rejected-boom");
            });
        }
    }

    /**
     * fetch 每轮抛 Error 的 store（C8-001 缺陷形态注入：Error 逃逸任务体）。
     */
    static IRetryRecordStore erroringStore(AtomicInteger fetchCount) {
        return (IRetryRecordStore) Proxy.newProxyInstance(IRetryRecordStore.class.getClassLoader(),
                new Class[]{IRetryRecordStore.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("fetchPendingRecords")) {
                        fetchCount.incrementAndGet();
                        throw new LinkageError("fetch-boom");
                    }
                    if (method.getName().equals("getCurrentTime"))
                        return System.currentTimeMillis();
                    if (method.getReturnType() == List.class)
                        return List.of();
                    if (method.getReturnType() == long.class)
                        return 0L;
                    return null;
                });
    }

    /**
     * 健康 store：fetch 返回空（每轮空扫描，测存活指标推进）。
     */
    static IRetryRecordStore emptyStore() {
        return (IRetryRecordStore) Proxy.newProxyInstance(IRetryRecordStore.class.getClassLoader(),
                new Class[]{IRetryRecordStore.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentTime"))
                        return System.currentTimeMillis();
                    if (method.getReturnType() == List.class)
                        return List.of();
                    if (method.getReturnType() == long.class)
                        return 0L;
                    return null;
                });
    }

    /**
     * C8-001 核心（P1 缺陷收敛面）：任务体每轮抛 Error 时扫描链仍存活多轮（绝缘设施重臂），
     * 失败轮次可观测（失败计数 + 最后失败时间），scanner 存活状态不说谎。
     * <p>
     * 红证据：把载体回退为裸 JDK 调度器（变异）时本用例红——首轮 Error 后链静默永久停摆
     * （fetchCount 停在 1），即审计确认的缺陷形态（快照 _tmp/phase3-retry-red.log）。
     */
    @Test
    void testScanChainSurvivesErrorRoundsAndStaysObservable() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger();
        BindCarrierScanner scanner = new BindCarrierScanner();
        liveScanners.add(scanner);
        scanner.setRecordStore(erroringStore(fetchCount));
        scanner.setScanIntervalMs(100);
        scanner.startScanning(batch -> {
        });

        long deadline = System.currentTimeMillis() + 15_000;
        while (fetchCount.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertTrue(fetchCount.get() >= 3,
                "C8-001: scan chain must survive Error rounds (re-arm), fetchCount=" + fetchCount.get());
        assertTrue(scanner.getScanFailureCount() >= 2, "Error rounds must be observable (failure count)");
        assertTrue(scanner.getLastScanFailureTimeMillis() > 0, "last failure time observable");
        assertTrue(scanner.getLastScanTimeMillis() > 0, "liveness: last scan time observable");
        assertTrue(scanner.getScanCount() >= 3, "scan round count observable");
    }

    /**
     * C8-001 存活指标（正常路径）：每轮扫描刷新最后扫描时间与轮数——停摆从此可判别
     * （lastScanTimeMillis 停止增长 + fetch 零命中 = 链死信号）。
     */
    @Test
    void testLivenessMetricsProgressOnHealthyRounds() throws Exception {
        BindCarrierScanner scanner = new BindCarrierScanner();
        liveScanners.add(scanner);
        scanner.setRecordStore(emptyStore());
        scanner.setScanIntervalMs(100);
        scanner.startScanning(batch -> {
        });

        long deadline = System.currentTimeMillis() + 10_000;
        while (scanner.getScanCount() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(scanner.getScanCount() >= 3, "healthy rounds counted");
        assertTrue(scanner.getLastScanTimeMillis() > 0);
        assertEquals(0, scanner.getScanFailureCount(), "healthy rounds have no failures");
        assertTrue(scanner.isRunning(), "healthy chain: running state stays true");
    }

    /**
     * C8-001 拒绝路径：设施级提交失败（链真死且不复活）时 running 状态真实化为 false，
     * 拒绝轮次计入失败观测——「running 仍 true 状态说谎」不再发生。
     */
    @Test
    void testChainRejectedMakesRunningFalseAndObservable() throws Exception {
        RejectingCarrierScanner scanner = new RejectingCarrierScanner();
        liveScanners.add(scanner);
        scanner.setRecordStore(emptyStore());
        scanner.setScanIntervalMs(100);
        scanner.startScanning(batch -> {
        });

        long deadline = System.currentTimeMillis() + 10_000;
        while (scanner.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(scanner.isRunning(), "rejection is a real chain death: running must be false");
        assertTrue(scanner.getScanFailureCount() >= 1, "rejection must be observable");
    }
}
