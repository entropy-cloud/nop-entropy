package io.nop.wf.dao.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.wf.dao.NopWfDaoConstants;
import io.nop.wf.dao.entity.NopWfDefinition;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: WfResourceNamespaceHandler 虚拟资源语义测试。
 * wf: 命名空间按版本化名字加载实体模型文本；未注册定义返回 UnknownResource。
 */
public class TestWfResourceNamespaceHandler {
    /**
     * DaoWorkflowModelLoader 替身：按名字返回固定实体（避免 ORM 依赖）。
     */
    private static DaoWorkflowModelLoader loaderReturning(NopWfDefinition entity) {
        return new DaoWorkflowModelLoader() {
            @Override
            public NopWfDefinition loadWfDefinition(String wfName, Long wfVersion) {
                return entity;
            }
        };
    }

    private static NopWfDefinition definition(String modelText, Timestamp updateTime) {
        NopWfDefinition entity = new NopWfDefinition();
        entity.setWfName("nop/wf/demo");
        entity.setWfVersion(1L);
        entity.setModelText(modelText);
        entity.setUpdateTime(updateTime);
        return entity;
    }

    @Test
    public void testNamespaceConstant() {
        assertEquals("wf", NopWfDaoConstants.NAMESPACE_WF);
        assertEquals(".xwf", NopWfDaoConstants.FILE_POSTFIX_XWF);
    }

    @Test
    public void testGetResourceReturnsModelTextWithLastModified() {
        WfResourceNamespaceHandler handler = new WfResourceNamespaceHandler();
        Timestamp updateTime = new Timestamp(1700000000000L);
        handler.setDaoWorkflowModelLoader(loaderReturning(
                definition("<workflow/>", updateTime)));

        IResource resource = handler.getResource("wf:/nop/wf/demo/v1.xwf", null);
        assertTrue(resource instanceof io.nop.core.resource.impl.InMemoryTextResource,
                "应将数据库模型文本包装为内存资源");
        assertTrue(resource.exists());
        // 模型文本作为资源内容可读
        assertEquals("<workflow/>", io.nop.core.resource.ResourceHelper.readText(resource, null));
        assertEquals(updateTime.getTime(), resource.lastModified(), "lastModified应来自实体updateTime");
    }

    @Test
    public void testUnknownDefinitionReturnsUnknownResource() {
        WfResourceNamespaceHandler handler = new WfResourceNamespaceHandler();
        handler.setDaoWorkflowModelLoader(loaderReturning(null));

        IResource resource = handler.getResource("wf:/nop/wf/missing/v1.xwf", null);
        assertTrue(resource instanceof UnknownResource);
        assertFalse(resource.exists());
    }
}
