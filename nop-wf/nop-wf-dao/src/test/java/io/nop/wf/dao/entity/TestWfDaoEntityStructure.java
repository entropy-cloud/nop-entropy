package io.nop.wf.dao.entity;

import io.nop.wf.dao.dataobject.WorkflowDefinitionDO;
import io.nop.wf.dao.entity._gen._NopWfDefinition;
import io.nop.wf.dao.entity._gen._NopWfInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI5: nop-wf-dao 实体结构性测试。
 * 属性名常量与 ORM 实体字段映射一致；实体字段读写回环；状态常量取值。
 */
public class TestWfDaoEntityStructure {
    @Test
    public void testDefinitionPropNameConstantsMatchFields() {
        // PROP_NAME_* 常量是 query/filter 的字段名契约，必须与实体属性名一致
        assertEquals("wfName", _NopWfDefinition.PROP_NAME_wfName);
        assertEquals("wfVersion", _NopWfDefinition.PROP_NAME_wfVersion);
        assertEquals("status", _NopWfDefinition.PROP_NAME_status);
        assertEquals("modelText", _NopWfDefinition.PROP_NAME_modelText);
    }

    @Test
    public void testInstancePropNameConstantsMatchFields() {
        assertEquals("wfName", _NopWfInstance.PROP_NAME_wfName);
        assertEquals("wfVersion", _NopWfInstance.PROP_NAME_wfVersion);
        assertEquals("status", _NopWfInstance.PROP_NAME_status);
    }

    @Test
    public void testDefinitionEntityFieldRoundtrip() {
        NopWfDefinition entity = new NopWfDefinition();
        assertNull(entity.getWfName());

        entity.setWfName("demo/approval");
        entity.setWfVersion(3L);
        entity.setStatus(1);
        entity.setModelText("<workflow/>");

        assertEquals("demo/approval", entity.getWfName());
        assertEquals(3L, entity.getWfVersion());
        assertEquals(1, entity.getStatus());
        assertEquals("<workflow/>", entity.getModelText());
    }

    @Test
    public void testDaoStatusConstants() {
        // 定义状态：0=未发布 1=已发布 2=归档
        assertEquals(0, io.nop.wf.core._NopWfCoreConstants.WF_DEF_STATUS_UNPUBLISHED);
        assertEquals(1, io.nop.wf.core._NopWfCoreConstants.WF_DEF_STATUS_PUBLISHED);
        assertEquals(2, io.nop.wf.core._NopWfCoreConstants.WF_DEF_STATUS_ARCHIVED);
    }

    @Test
    public void testWorkflowDefinitionDoBinding() {
        // WorkflowDefinitionDO 与实体绑定：getSourceObject 暴露原实体
        NopWfDefinition entity = new NopWfDefinition();
        entity.setWfName("demo/approval");
        WorkflowDefinitionDO wfDef = new WorkflowDefinitionDO(null, null, null, entity);

        assertEquals(entity, wfDef.getSourceObject());
        assertEquals("demo/approval", wfDef.getSourceObject().getWfName());
        assertNull(wfDef.parseWorkflowModel(), "空模型文本解析返回null");
    }
}
