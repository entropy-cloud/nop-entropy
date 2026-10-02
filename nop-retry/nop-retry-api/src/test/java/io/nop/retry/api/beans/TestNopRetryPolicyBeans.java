package io.nop.retry.api.beans;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结构性验证 retry policy bean 的读写契约：input/output bean 的字段
 * setter 写入后 getter 必须原样返回（序列化契约的基础）。
 */
public class TestNopRetryPolicyBeans {

    @Test
    public void testPolicyInputBeanRoundTrip() {
        NopRetryPolicyInputBean bean = new NopRetryPolicyInputBean();
        bean.setSid("sid-1");
        bean.setNamespaceId("ns");
        bean.setGroupId("grp");
        bean.setName("policy-a");
        bean.setStatus("1");
        bean.setMaxRetryCount(5);
        bean.setBackoffStrategy(2);
        bean.setInitialIntervalMs(1000L);
        bean.setMaxIntervalMs(60000L);
        bean.setJitterRatio(0.5);
        bean.setDeadlineTimeoutMs(86400000L);
        bean.setBlockStrategy(1);
        bean.setCallbackEnabled("1");
        bean.setCallbackPolicyId("policy-b");
        bean.setDescription("test policy");

        assertEquals("sid-1", bean.getSid());
        assertEquals("ns", bean.getNamespaceId());
        assertEquals("grp", bean.getGroupId());
        assertEquals("policy-a", bean.getName());
        assertEquals("1", bean.getStatus());
        assertEquals(5, bean.getMaxRetryCount());
        assertEquals(2, bean.getBackoffStrategy());
        assertEquals(1000L, bean.getInitialIntervalMs());
        assertEquals(60000L, bean.getMaxIntervalMs());
        assertEquals(0.5, bean.getJitterRatio(), 1e-9);
        assertEquals(86400000L, bean.getDeadlineTimeoutMs());
        assertEquals(1, bean.getBlockStrategy());
        assertEquals("1", bean.getCallbackEnabled());
        assertEquals("policy-b", bean.getCallbackPolicyId());
        assertEquals("test policy", bean.getDescription());
    }

    @Test
    public void testPolicyOutputBeanRoundTrip() {
        NopRetryPolicyOutputBean bean = new NopRetryPolicyOutputBean();
        bean.setSid("sid-2");
        bean.setOwnerId("owner-1");
        bean.setMaxRetryCount(3);
        bean.setBackoffStrategy_label("exponential");
        bean.setUpdateTime(new Timestamp(123L));

        assertEquals("sid-2", bean.getSid());
        assertEquals("owner-1", bean.getOwnerId());
        assertEquals(3, bean.getMaxRetryCount());
        assertEquals("exponential", bean.getBackoffStrategy_label(),
                "dict label fields must round-trip like plain fields");
        assertEquals(new Timestamp(123L), bean.getUpdateTime());
    }

    @Test
    public void testPolicyOutputLabelFieldsIndependent() {
        NopRetryPolicyOutputBean bean = new NopRetryPolicyOutputBean();
        bean.setBlockStrategy(1);
        bean.setBlockStrategy_label("discard");

        assertEquals(1, bean.getBlockStrategy());
        assertEquals("discard", bean.getBlockStrategy_label());
    }
}
