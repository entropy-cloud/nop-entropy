/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.impl.RetryTaskImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryRecordStoreImpl 数据库测试。
 * <p>
 * 本环境 H2 init-database-schema 受限（42S04，见 plan 340/341 基线），
 * 用例按静态核对口径编写，逻辑与断言以 plan 342 Phase 4 为准。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestRetryRecordStoreImpl extends JunitAutoTestCase {

    @Inject
    IRetryRecordStore recordStore;

    @Inject
    IDaoProvider daoProvider;

    @Test
    void testNewRecord_shouldAssignDeterministicPartitionIndex() {
        NopRetryPolicy policy = createPolicy("policy-p1");
        recordStore.savePolicy(policy);

        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(java.util.Map.of("k", "v"));

        IRetryTask task1 = newTask("svc", "method", "policy-p1", "idem-p1");
        IRetryTask task1Again = newTask("svc", "method", "policy-p1", "idem-p1");
        IRetryTask task2 = newTask("svc", "method", "policy-p1", "idem-p2");

        NopRetryRecord r1 = recordStore.newRecord(task1, request);
        NopRetryRecord r1Again = recordStore.newRecord(task1Again, request);
        NopRetryRecord r2 = recordStore.newRecord(task2, request);

        // 同幂等键同分区；分区在有效范围
        assertEquals(r1.getPartitionIndex(), r1Again.getPartitionIndex());
        assertTrue(r1.getPartitionIndex() >= 0);
        assertTrue(r1.getPartitionIndex() < DEFAULT_PARTITION_COUNT);
        assertTrue(r2.getPartitionIndex() >= 0);
        assertTrue(r2.getPartitionIndex() < DEFAULT_PARTITION_COUNT);
    }

    @Test
    void testFetchPendingRecords_shouldApplyFilters() {
        NopRetryPolicy policy = createPolicy("policy-fetch");
        recordStore.savePolicy(policy);

        NopRetryRecord due = createRecord("rec-due", RETRY_RECORD_STATUS_PENDING);
        due.setNextTriggerTime(new Timestamp(System.currentTimeMillis() - 1000));
        recordStore.saveRecord(due);

        NopRetryRecord notDue = createRecord("rec-not-due", RETRY_RECORD_STATUS_PENDING);
        notDue.setNextTriggerTime(new Timestamp(System.currentTimeMillis() + 10000));
        recordStore.saveRecord(notDue);

        NopRetryRecord completed = createRecord("rec-completed", RETRY_RECORD_STATUS_COMPLETED);
        completed.setNextTriggerTime(new Timestamp(System.currentTimeMillis() - 1000));
        recordStore.saveRecord(completed);

        List<NopRetryRecord> records = recordStore.fetchPendingRecords(100, null);

        // 只包含到期且状态为 PENDING/RETRYING 的记录
        assertTrue(records.stream().anyMatch(r -> r.getSid().equals("rec-due")));
        assertTrue(records.stream().noneMatch(r -> r.getSid().equals("rec-not-due")));
        assertTrue(records.stream().noneMatch(r -> r.getSid().equals("rec-completed")));
    }

    @Test
    void testFetchPendingRecords_shouldFilterByPartition() {
        NopRetryPolicy policy = createPolicy("policy-part");
        recordStore.savePolicy(policy);

        ApiRequest<Object> request = new ApiRequest<>();
        NopRetryRecord r1 = recordStore.newRecord(newTask("svc", "m", "policy-part", "idem-a"), request);
        r1.setSid("part-rec-a");
        r1.setNextTriggerTime(new Timestamp(System.currentTimeMillis() - 1000));
        recordStore.saveRecord(r1);

        // 只查询 r1 所在分区
        IntRangeSet partitions = IntRangeSet.rangeSet(
                List.of(new IntRangeBean(r1.getPartitionIndex(), r1.getPartitionIndex())));

        List<NopRetryRecord> records = recordStore.fetchPendingRecords(100, partitions);
        assertTrue(records.stream().allMatch(r -> r.getPartitionIndex().equals(r1.getPartitionIndex())));
        assertTrue(records.stream().anyMatch(r -> r.getSid().equals("part-rec-a")));
    }

    @Test
    void testFindPendingRecordByIdempotentId_shouldIgnoreTerminalRecords() {
        NopRetryPolicy policy = createPolicy("policy-idem");
        recordStore.savePolicy(policy);

        NopRetryRecord pending = createRecord("idem-pending", RETRY_RECORD_STATUS_PENDING);
        pending.setIdempotentId("idem-query");
        recordStore.saveRecord(pending);

        assertEquals("idem-pending", recordStore.findPendingRecordByIdempotentId("idem-query").getSid());

        // 终态记录不再被视为 pending
        pending.setStatus(RETRY_RECORD_STATUS_COMPLETED);
        recordStore.updateRecord(pending);
        assertNull(recordStore.findPendingRecordByIdempotentId("idem-query"));
    }

    @Test
    void testTryLockRecordsForProcess_shouldMovePendingToRetrying() {
        NopRetryPolicy policy = createPolicy("policy-lock");
        recordStore.savePolicy(policy);

        NopRetryRecord pending = createRecord("lock-pending", RETRY_RECORD_STATUS_PENDING);
        recordStore.saveRecord(pending);

        List<NopRetryRecord> locked = recordStore.tryLockRecordsForProcess(List.of(pending), 60000L);

        assertEquals(1, locked.size());
        assertEquals(RETRY_RECORD_STATUS_RETRYING, locked.get(0).getStatus());
        assertNotNull(locked.get(0).getNextTriggerTime());
    }

    @Test
    void testMoveToDeadLetter_shouldDeleteOriginalRecord() {
        NopRetryPolicy policy = createPolicy("policy-dl");
        recordStore.savePolicy(policy);

        NopRetryRecord record = createRecord("dl-record", RETRY_RECORD_STATUS_PENDING);
        record.setIdempotentId("idem-dl");
        record.setPolicyId("policy-dl");
        record.setRequestPayload("{\"data\":\"x\"}");
        recordStore.saveRecord(record);

        recordStore.moveToDeadLetter(record, "ERR_CODE", "err message", null);

        // 死信行存在
        List<NopRetryDeadLetter> deadLetters = findDeadLettersByIdempotentId("idem-dl");
        assertEquals(1, deadLetters.size());
        assertEquals("ERR_CODE", deadLetters.get(0).getFailureCode());
        assertNotNull(deadLetters.get(0).getRecordId());

        // 原 record 行已删除（幂等键可复用）
        assertNull(recordStore.findPendingRecordByIdempotentId("idem-dl"));
    }

    // ==================== Helpers ====================

    private List<NopRetryDeadLetter> findDeadLettersByIdempotentId(String idempotentId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("idempotentId", idempotentId));
        query.addOrderField("createTime", true);
        return daoProvider.daoFor(NopRetryDeadLetter.class).findAllByQuery(query);
    }

    private IRetryTask newTask(String serviceName, String serviceMethod, String policyId, String idempotentId) {
        RetryTaskImpl task = new RetryTaskImpl(null, serviceName, serviceMethod);
        task.withPolicyId(policyId);
        task.withIdempotentId(idempotentId);
        return task;
    }

    private NopRetryPolicy createPolicy(String sid) {
        NopRetryPolicy policy = new NopRetryPolicy();
        policy.setSid(sid);
        policy.setName("Policy " + sid);
        policy.setNamespaceId("default");
        policy.setGroupId("default");
        policy.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        policy.setBlockStrategy(BLOCK_STRATEGY_PARALLEL);
        policy.setImmediateRetryCount(0);
        return policy;
    }

    private NopRetryRecord createRecord(String sid, int status) {
        NopRetryRecord record = new NopRetryRecord();
        record.setSid(sid);
        record.setNamespaceId("default");
        record.setGroupId("default");
        record.setPolicyId("policy-fetch");
        record.setIdempotentId("idem-" + sid);
        record.setStatus(status);
        record.setRetryCount(0);
        record.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        record.setServiceName("svc");
        record.setServiceMethod("method");
        return record;
    }
}
