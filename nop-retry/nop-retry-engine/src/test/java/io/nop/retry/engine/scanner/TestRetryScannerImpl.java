/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.scanner;

import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.store.IRetryRecordStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryScannerImpl 单元测试：mock store 与 executor，不依赖数据库。
 */
class TestRetryScannerImpl {

    /**
     * 使用 noop executor 的扫描器子类：startScanning 不产生真实调度
     */
    static class TestScanner extends RetryScannerImpl {

        @Override
        protected IScheduledExecutor getExecutor() {
            return createNoopExecutor();
        }
    }

    static class StoreStub implements IRetryRecordStore {
        Supplier<List<NopRetryRecord>> fetchSupplier = List::of;
        Supplier<List<NopRetryRecord>> lockSupplier = List::of;
        int fetchCount;
        int lockCount;

        @Override
        public List<NopRetryRecord> fetchPendingRecords(int limit, io.nop.api.core.beans.IntRangeSet partitions) {
            fetchCount++;
            return fetchSupplier.get();
        }

        @Override
        public List<NopRetryRecord> tryLockRecordsForProcess(List<NopRetryRecord> records, long retryingTimeoutMs) {
            lockCount++;
            return lockSupplier.get();
        }

        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryRecord newRecord(
                io.nop.retry.api.IRetryTask task, io.nop.api.core.beans.ApiRequest<?> request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryAttempt newAttempt(
                io.nop.retry.dao.entity.NopRetryRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveAttempt(io.nop.retry.dao.entity.NopRetryAttempt attempt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryRecord loadRecord(String recordId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryRecord findPendingRecordByIdempotentId(String idempotentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRecord(io.nop.retry.dao.entity.NopRetryRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryDeadLetter loadDeadLetter(String deadLetterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveDeadLetter(io.nop.retry.dao.entity.NopRetryDeadLetter deadLetter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveRecord(io.nop.retry.dao.entity.NopRetryRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRecord(io.nop.retry.dao.entity.NopRetryRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void moveToDeadLetter(io.nop.retry.dao.entity.NopRetryRecord record, String errorCode,
                                     String errorMessage, String errorStack) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void savePolicy(io.nop.retry.dao.entity.NopRetryPolicy policy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.nop.retry.dao.entity.NopRetryPolicy loadPolicy(String policyId) {
            throw new UnsupportedOperationException();
        }
    }

    private static IScheduledExecutor createNoopExecutor() {
        return (IScheduledExecutor) Proxy.newProxyInstance(
                IScheduledExecutor.class.getClassLoader(),
                new Class[]{IScheduledExecutor.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("scheduleWithFixedDelay") || name.equals("scheduleAtFixedRate")
                            || name.equals("schedule") || name.equals("submit")) {
                        return CompletableFuture.completedFuture(null);
                    }
                    if (method.getReturnType() == Future.class) {
                        return CompletableFuture.completedFuture(null);
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    private static void setRunning(RetryScannerImpl scanner, boolean running) {
        try {
            Field field = RetryScannerImpl.class.getDeclaredField("running");
            field.setAccessible(true);
            field.set(scanner, running);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StoreStub store;
    private TestScanner scanner;
    private final List<List<NopRetryRecord>> processedBatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = new StoreStub();
        processedBatches.clear();
        scanner = new TestScanner();
        scanner.setRecordStore(store);
        scanner.setBatchSize(100);
        scanner.setScanIntervalMs(5000);
    }

    private static NopRetryRecord record(String sid) {
        NopRetryRecord record = new NopRetryRecord();
        record.setSid(sid);
        return record;
    }

    @Test
    void testStartStopScanning_isIdempotent() {
        scanner.startScanning(processedBatches::add);
        scanner.startScanning(processedBatches::add);
        scanner.stopScanning();
        scanner.stopScanning();
    }

    @Test
    void testDoScan_fetchesLocksAndProcesses() {
        NopRetryRecord r1 = record("r1");
        store.fetchSupplier = () -> List.of(r1);
        store.lockSupplier = () -> List.of(r1);
        setRunning(scanner, true);

        // 处理完第一轮后置 running=false，终止 doScan 循环
        scanner.doScan(batch -> {
            processedBatches.add(batch);
            setRunning(scanner, false);
        });

        assertEquals(1, store.fetchCount);
        assertEquals(1, store.lockCount);
        assertEquals(1, processedBatches.size());
        assertEquals("r1", processedBatches.get(0).get(0).getSid());
    }

    @Test
    void testDoScan_fetchEmpty_stopsWithoutProcessing() {
        setRunning(scanner, true);
        scanner.doScan(processedBatches::add);

        assertEquals(1, store.fetchCount);
        assertEquals(0, store.lockCount);
        assertTrue(processedBatches.isEmpty());
    }

    @Test
    void testDoScan_lockEmpty_retriesNextBatch() {
        NopRetryRecord r1 = record("r1");
        store.fetchSupplier = new Supplier<>() {
            int call;

            @Override
            public List<NopRetryRecord> get() {
                call++;
                return call == 1 ? List.of(r1) : List.of();
            }
        };
        store.lockSupplier = List::of;
        setRunning(scanner, true);

        scanner.doScan(processedBatches::add);

        // 第一轮 fetch→lock 空→continue→第二轮 fetch 空→break
        assertEquals(2, store.fetchCount);
        assertEquals(1, store.lockCount);
        assertTrue(processedBatches.isEmpty());
    }

    @Test
    void testDoScan_processorException_doesNotPropagate() {
        NopRetryRecord r1 = record("r1");
        store.fetchSupplier = new Supplier<>() {
            int call;

            @Override
            public List<NopRetryRecord> get() {
                call++;
                return call == 1 ? List.of(r1) : List.of();
            }
        };
        store.lockSupplier = () -> List.of(r1);
        setRunning(scanner, true);

        assertDoesNotThrow(() -> scanner.doScan(batch -> {
            throw new RuntimeException("processor failed");
        }));
        assertEquals(2, store.fetchCount);
        assertEquals(1, store.lockCount);
    }

    @Test
    void testDoScan_stopsWhenRunningFalse() {
        // running=false（未 startScanning）时 doScan 直接返回
        scanner.doScan(processedBatches::add);

        assertEquals(0, store.fetchCount);
        assertTrue(processedBatches.isEmpty());
    }
}
