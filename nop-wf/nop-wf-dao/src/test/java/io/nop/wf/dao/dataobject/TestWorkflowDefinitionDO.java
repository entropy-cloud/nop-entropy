package io.nop.wf.dao.dataobject;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.dao.entity.NopWfDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_DEF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_EMPTY_MODEL_TEXT;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_PARSE_MODEL_TEXT_FAIL;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI5: WorkflowDefinitionDO 模型解析/校验语义测试。
 * 空模型文本拒绝（ERR_WF_EMPTY_MODEL_TEXT）、坏 XML 包装为
 * ERR_WF_PARSE_MODEL_TEXT_FAIL 且携带 wfName/wfVersion/wfDefId 定位参数、
 * 合法模型解析产物字段正确、DO 与实体绑定缓存语义。
 */
public class TestWorkflowDefinitionDO extends BaseTestCase {
    private static final String VALID_MODEL =
            "<workflow wfName=\"dao-model\" wfVersion=\"7\""
                    + " x:schema=\"/nop/schema/wf/wf.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">"
                    + "<start startStepName=\"wf-start\"/>"
                    + "<steps><step name=\"wf-start\"><assignment><actors>"
                    + "<actor actorId=\"1\" actorType=\"user\" actorModelId=\"m1\"/>"
                    + "</actors></assignment><transition><to-end/></transition></step></steps>"
                    + "</workflow>";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * IWorkflowManager 替身：仅实现 parseWorkflowNode，直接委托产品 WfModelParser。
     */
    private static io.nop.wf.core.IWorkflowManager manager() {
        return (io.nop.wf.core.IWorkflowManager) Proxy.newProxyInstance(
                io.nop.wf.core.IWorkflowManager.class.getClassLoader(),
                new Class[]{io.nop.wf.core.IWorkflowManager.class},
                (proxy, method, args) -> {
                    if ("parseWorkflowNode".equals(method.getName()))
                        return io.nop.wf.core.store.WfModelParser.parseWorkflowNode((XNode) args[0]);
                    return null;
                });
    }

    private static NopWfDefinition definition(String modelText) {
        NopWfDefinition entity = new NopWfDefinition();
        entity.setWfName("dao-model");
        entity.setWfVersion(7L);
        entity.setWfDefId("def-1");
        entity.setModelText(modelText);
        return entity;
    }

    @Test
    public void testBlankModelTextParsesToNullAndValidateRejects() {
        WorkflowDefinitionDO wfDef = new WorkflowDefinitionDO(null, null, manager(),
                definition("   "));

        // 空文本：解析返回 null；validate 显式报错并携带定义定位参数
        assertNull(wfDef.parseWorkflowModel());

        NopException e = assertThrows(NopException.class, wfDef::validateModel);
        assertEquals(ERR_WF_EMPTY_MODEL_TEXT.getErrorCode(), e.getErrorCode());
        assertEquals("dao-model", e.getParam(ARG_WF_NAME));
        assertEquals(7L, e.getParam(ARG_WF_VERSION));
        assertEquals("def-1", e.getParam(ARG_WF_DEF_ID));
    }

    @Test
    public void testMalformedModelTextWrappedAsParseFailure() {
        WorkflowDefinitionDO wfDef = new WorkflowDefinitionDO(null, null, manager(),
                definition("<workflow x:schema=\"/nop/schema/wf/wf.xdef\""));

        try {
            wfDef.parseWorkflowModel();
            fail("expect ERR_WF_PARSE_MODEL_TEXT_FAIL");
        } catch (NopException e) {
            assertEquals(ERR_WF_PARSE_MODEL_TEXT_FAIL.getErrorCode(), e.getErrorCode());
            assertEquals("dao-model", e.getParam(ARG_WF_NAME));
            assertEquals(7L, e.getParam(ARG_WF_VERSION));
            assertNotNull(e.getCause(), "必须保留底层解析异常");
        }
    }

    @Test
    public void testValidModelTextParsesToWfModel() {
        WorkflowDefinitionDO wfDef = new WorkflowDefinitionDO(null, null, manager(),
                definition(VALID_MODEL));

        WfModel model = (WfModel) wfDef.parseWorkflowModel();
        assertNotNull(model);
        assertEquals("dao-model", model.getWfName());
        assertEquals(7L, model.getWfVersion());
        assertEquals("wf-start", model.getStart().getStartStepName());
        assertTrue(wfDef.getSourceObject() instanceof NopWfDefinition);
    }

    @Test
    public void testModelTextWithInvalidWorkflowStructureRejected() {
        // 引用不存在的起始步骤：DO 解析层把内部校验失败统一包装为
        // ERR_WF_PARSE_MODEL_TEXT_FAIL，具体原因保留在 cause 链中
        WorkflowDefinitionDO wfDef = new WorkflowDefinitionDO(null, null, manager(),
                definition("<workflow wfName=\"dao-model\" wfVersion=\"7\""
                        + " x:schema=\"/nop/schema/wf/wf.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">"
                        + "<start startStepName=\"ghost\"/>"
                        + "<steps><step name=\"wf-start\"><transition><to-end/></transition></step></steps>"
                        + "</workflow>"));

        NopException e = assertThrows(NopException.class, wfDef::parseWorkflowModel);
        assertEquals(ERR_WF_PARSE_MODEL_TEXT_FAIL.getErrorCode(), e.getErrorCode());

        Throwable cause = e.getCause();
        while (cause != null && !(cause instanceof NopException
                && ERR_WF_UNKNOWN_STEP.getErrorCode().equals(((NopException) cause).getErrorCode()))) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "cause链中应保留ERR_WF_UNKNOWN_STEP");
        assertEquals("ghost", ((NopException) cause).getParam(ARG_STEP_NAME));
    }

    @Test
    public void testDefinitionDocCachesPerEntity() {
        NopWfDefinition entity = definition(VALID_MODEL);
        DefaultWorkflowDOProvider provider = new DefaultWorkflowDOProvider();
        // daoProvider/ormTemplate/workflowManager 在本用例路径中不被调用，置空验证绑定语义
        provider.setDaoProvider(null);
        provider.setOrmTemplate(null);
        provider.setWorkflowManager(manager());

        IServiceContext ctx = new io.nop.core.context.ServiceContextImpl();
        IWorkflowDefinitionDO first = provider.getWorkflowDefinitionDO(entity, ctx);
        IWorkflowDefinitionDO second = provider.getWorkflowDefinitionDO(entity, ctx);

        // 同一实体复用同一 DO 实例（computeIfAbsent 缓存语义）
        assertSame(first, second);
        assertSame(entity, first.getSourceObject());
    }
}
