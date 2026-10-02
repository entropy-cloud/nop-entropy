package io.nop.retry.api.beans;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结构性验证 retry record / attempt / dead letter bean 的读写契约。
 * record 是重试调度的事实记录，attempt 是执行流水，dead letter 是最终失败的兜底存储；
 * 三者通过 recordId / idempotentId 关联，字段读写必须保持稳定。
 */
public class TestNopRetryRecordBeans {

    @Test
    public void testRecordOutputBeanRoundTrip() {
        NopRetryRecordOutputBean bean = new NopRetryRecordOutputBean();
        bean.setSid("r-1");
        bean.setNamespaceId("ns");
        bean.setGroupId("grp");
        bean.setPolicyId("policy-1");
        bean.setIdempotentId("idem-1");
        bean.setStatus(1);
        bean.setStatus_label("retrying");
        bean.setRetryCount(2);
        bean.setMaxRetryCount(3);
        bean.setNextTriggerTime(new Timestamp(1000L));
        bean.setPartitionIndex(7);
        bean.setExecutorName("defaultExecutor");
        bean.setServiceName("svc");
        bean.setServiceMethod("doIt");
        bean.setRequestPayload("{}");
        bean.setPolicy(Map.of("maxRetryCount", 3));

        assertEquals("r-1", bean.getSid());
        assertEquals("policy-1", bean.getPolicyId());
        assertEquals("idem-1", bean.getIdempotentId());
        assertEquals(1, bean.getStatus());
        assertEquals("retrying", bean.getStatus_label());
        assertEquals(2, bean.getRetryCount());
        assertEquals(3, bean.getMaxRetryCount());
        assertEquals(7, bean.getPartitionIndex());
        assertEquals("defaultExecutor", bean.getExecutorName());
        assertEquals("svc", bean.getServiceName());
        assertEquals("doIt", bean.getServiceMethod());
        assertEquals(Map.of("maxRetryCount", 3), bean.getPolicy());
    }

    @Test
    public void testRecordInputBeanRoundTrip() {
        NopRetryRecordInputBean bean = new NopRetryRecordInputBean();
        bean.setSid("r-2");
        bean.setIdempotentId("idem-2");
        bean.setRetryCount(1);

        assertEquals("r-2", bean.getSid());
        assertEquals("idem-2", bean.getIdempotentId());
        assertEquals(1, bean.getRetryCount());
    }

    @Test
    public void testAttemptOutputBeanRoundTrip() {
        NopRetryAttemptOutputBean bean = new NopRetryAttemptOutputBean();
        bean.setSid("a-1");
        bean.setRecordId("r-1");
        bean.setAttemptNo(2);
        bean.setStatus(1);
        bean.setStartTime(new Timestamp(1L));
        bean.setEndTime(new Timestamp(101L));
        bean.setDurationMs(100L);
        bean.setErrorCode("ERR_X");
        bean.setErrorMessage("failed");
        bean.setClientAddress("10.0.0.1");

        assertEquals("a-1", bean.getSid());
        assertEquals("r-1", bean.getRecordId(), "attempt must reference its retry record");
        assertEquals(2, bean.getAttemptNo());
        assertEquals(1, bean.getStatus());
        assertEquals(100L, bean.getDurationMs());
        assertEquals("ERR_X", bean.getErrorCode());
        assertEquals("failed", bean.getErrorMessage());
        assertEquals("10.0.0.1", bean.getClientAddress());
    }

    @Test
    public void testDeadLetterOutputBeanRoundTrip() {
        NopRetryDeadLetterOutputBean bean = new NopRetryDeadLetterOutputBean();
        bean.setSid("d-1");
        bean.setRecordId("r-1");
        bean.setIdempotentId("idem-1");
        bean.setFinalStatus(3);
        bean.setFinalStatus_label("dead");
        bean.setFailureCode("ERR_FINAL");
        bean.setFailureMessage("all retries exhausted");
        bean.setServiceName("svc");
        bean.setServiceMethod("doIt");

        assertEquals("d-1", bean.getSid());
        assertEquals("r-1", bean.getRecordId(), "dead letter must reference its retry record");
        assertEquals("idem-1", bean.getIdempotentId());
        assertEquals(3, bean.getFinalStatus());
        assertEquals("dead", bean.getFinalStatus_label());
        assertEquals("ERR_FINAL", bean.getFailureCode());
        assertEquals("all retries exhausted", bean.getFailureMessage());
        assertEquals("svc", bean.getServiceName());
        assertEquals("doIt", bean.getServiceMethod());
    }

    @Test
    public void testDeadLetterInputBeanRoundTrip() {
        NopRetryDeadLetterInputBean bean = new NopRetryDeadLetterInputBean();
        bean.setSid("d-2");
        bean.setPolicyId("policy-1");
        bean.setErrorStack("stack");

        assertEquals("d-2", bean.getSid());
        assertEquals("policy-1", bean.getPolicyId());
        assertEquals("stack", bean.getErrorStack());
    }

    @Test
    public void testAttemptInputBeanRoundTrip() {
        NopRetryAttemptInputBean bean = new NopRetryAttemptInputBean();
        bean.setSid("a-2");
        bean.setRecordId("r-2");
        bean.setReason("manual");

        assertEquals("a-2", bean.getSid());
        assertEquals("r-2", bean.getRecordId());
        assertEquals("manual", bean.getReason());
    }
}
