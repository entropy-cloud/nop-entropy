package io.nop.wf.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.wf.api.beans.WfActionRequestBean;
import io.nop.wf.api.beans.WfStartRequestBean;
import io.nop.wf.api.beans.WfStartResponseBean;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI5: nop-wf-api 对象契约结构测试。引用对象标识格式与请求/响应 bean 的字段映射。
 */
public class TestWfApiBeanContracts {
    @Test
    public void testWfReferenceIdentityFormat() {
        WfReference ref = new WfReference("demo/approval", 1L, "wf-1");
        assertEquals("demo/approval", ref.getWfName());
        assertEquals(1L, ref.getWfVersion());
        assertEquals("wf-1", ref.getWfId());
        // toString 契约: wfName-wfVersion:wfId
        assertEquals("demo/approval-1:wf-1", ref.toString());
    }

    @Test
    public void testWfStepReferenceIdentityFormat() {
        WfStepReference ref = new WfStepReference("demo/approval", 2L, "wf-1", "step-9");
        assertEquals("step-9", ref.getStepId());
        assertEquals("demo/approval-2:wf-1:step-9", ref.toString());
    }

    @Test
    public void testWfActionRequestBeanFields() {
        WfActionRequestBean bean = new WfActionRequestBean();
        assertNull(bean.getWfId());
        assertNull(bean.getActionName());

        bean.setWfId("wf-1");
        bean.setStepId("step-1");
        bean.setActionName("agree");
        bean.setArgs(Map.of("opinion", "ok"));

        assertEquals("wf-1", bean.getWfId());
        assertEquals("step-1", bean.getStepId());
        assertEquals("agree", bean.getActionName());
        assertEquals("ok", bean.getArgs().get("opinion"));
    }

    @Test
    public void testWfStartRequestBeanFields() {
        WfStartRequestBean bean = new WfStartRequestBean();
        assertNull(bean.getWfName());
        bean.setWfName("demo/approval");
        bean.setWfVersion(1L);
        bean.setWfParams(Map.of("bizId", "b1"));
        bean.setParentWfName("demo/parent");
        bean.setParentWfId("wf-parent");

        assertEquals("demo/approval", bean.getWfName());
        assertEquals(1L, bean.getWfVersion());
        assertEquals("b1", bean.getWfParams().get("bizId"));
        assertEquals("demo/parent", bean.getParentWfName());
        assertEquals("wf-parent", bean.getParentWfId());
    }

    @Test
    public void testWfStartResponseBeanFields() {
        WfStartResponseBean bean = new WfStartResponseBean();
        assertNull(bean.getWfName());

        bean.setWfName("demo/approval");
        bean.setWfVersion(1L);
        bean.setWfId("wf-1");
        bean.setManagerType("user");
        bean.setManagerName("u1");

        assertEquals("demo/approval", bean.getWfName());
        assertEquals(1L, bean.getWfVersion());
        assertEquals("wf-1", bean.getWfId());
        assertEquals("user", bean.getManagerType());
        assertEquals("u1", bean.getManagerName());
    }

    @Test
    public void testApiRequestWrapperUnchanged() {
        // 确认引用类型可放入通用 ApiRequest 结构（跨模块序列化契约）
        ApiRequest<WfReference> request = new ApiRequest<>();
        WfReference ref = new WfReference("demo/approval", 1L, "wf-1");
        request.setData(ref);
        assertEquals(ref, request.getData());
    }
}
