package io.nop.wf.core.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import static io.nop.wf.core.NopWfCoreErrors.ARG_ACTION_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_LOOP_EDGES;
import static io.nop.wf.core.NopWfCoreErrors.ARG_OTHER_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_GRAPH_CONTAINS_LOOP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_MULTIPLE_STEP_REF_SAME_ACTION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_STEP_REF_ACTION_IS_COMMON;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TO_UNKNOWN_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI5: WfModelParser 校验错误路径测试。断言 WfModelAnalyzer 对非法流程定义的
 * 拒绝语义：错误码与定位参数必须准确，防止模型校验被静默跳过。
 */
public class TestWfModelParserValidation extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private NopException assertParseFails(String path, String expectedErrorCode) {
        try {
            WfModel model = WfModelParser.parseWorkflowModel(getResource(path));
            fail("expect parse failure for " + path + ", but got model " + model.getWfName());
            throw new TestAbortedException("unreachable");
        } catch (NopException e) {
            assertEquals(expectedErrorCode, e.getErrorCode());
            return e;
        }
    }

    @Test
    public void testUnknownStartStepRejected() {
        NopException e = assertParseFails("/nop/wf/test/errUnknownStart/v1.xwf",
                ERR_WF_UNKNOWN_STEP.getErrorCode());
        assertEquals("ghost", e.getParam(ARG_STEP_NAME));
    }

    @Test
    public void testTransitionToUnknownStepRejected() {
        NopException e = assertParseFails("/nop/wf/test/errUnknownToStep/v1.xwf",
                ERR_WF_TRANSITION_TO_UNKNOWN_STEP.getErrorCode());
        assertEquals("ghost", e.getParam(ARG_STEP_NAME));
    }

    @Test
    public void testGraphLoopRejected() {
        // a -> b -> a 构成环且两条边均未标记 backLink（环不包含根节点）
        NopException e = assertParseFails("/nop/wf/test/errLoop/v1.xwf",
                ERR_WF_GRAPH_CONTAINS_LOOP.getErrorCode());
        assertNotNull(e.getParam(ARG_LOOP_EDGES), "环错误必须携带loopEdges定位参数");
    }

    @Test
    public void testRootCycleCrashesBeforeFriendlyLoopError() {
        // 缺陷记录用例（plan 2297，不修产品）：环包含根节点时，
        // GraphBreadthFirstIterator 未将 root 放入 visited 集合，
        // checkStartReachable 先于环校验抛出 ArrayIndexOutOfBoundsException。
        // 若未来修复该缺陷，本用例应改为断言 ERR_WF_GRAPH_CONTAINS_LOOP。
        try {
            WfModel model = WfModelParser.parseWorkflowModel(getResource("/nop/wf/test/errRootLoop/v1.xwf"));
            fail("expect crash or friendly loop error, but parsed " + model.getWfName());
        } catch (ArrayIndexOutOfBoundsException e) {
            // 当前缺陷行为：越界异常先于 ERR_WF_GRAPH_CONTAINS_LOOP 发生
        }
    }

    @Test
    public void testStepNotEndableValidationIsDeadCode() {
        // 缺陷记录用例（plan 2297，不修产品）：WfModelAnalyzer.checkEnd 的
        // eventuallyToAssigned 传播缺少 isNextToAssigned 前置条件，任何步骤都会被
        // 无条件标记为 eventuallyToAssigned，导致 ERR_WF_STEP_NOT_ENDABLE 不可达：
        // dead 步骤没有任何出边（无 transition、无 action），解析仍然成功。
        WfModel model = WfModelParser.parseWorkflowModel(getResource("/nop/wf/test/errNotEndable/v1.xwf"));
        WfStepModel dead = model.getStep("dead");
        assertNotNull(dead);
        assertTrue(dead.getTransitionToStepNames().isEmpty());
        assertTrue(dead.isEventuallyToAssigned(), "缺陷签名：无出边步骤仍被标记eventuallyToAssigned");
        assertFalse(dead.isEventuallyToEnd());
    }

    @Test
    public void testRefCommonActionRejected() {
        NopException e = assertParseFails("/nop/wf/test/errRefCommon/v1.xwf",
                ERR_WF_STEP_REF_ACTION_IS_COMMON.getErrorCode());
        assertEquals("wf-start", e.getParam(ARG_STEP_NAME));
        assertEquals("sh", e.getParam(ARG_ACTION_NAME));
    }

    @Test
    public void testDuplicateRefActionRejected() {
        NopException e = assertParseFails("/nop/wf/test/errDupRef/v1.xwf",
                ERR_WF_MULTIPLE_STEP_REF_SAME_ACTION.getErrorCode());
        assertEquals("sh", e.getParam(ARG_ACTION_NAME));
        assertEquals("second", e.getParam(ARG_STEP_NAME));
        assertEquals("wf-start", e.getParam(ARG_OTHER_STEP_NAME));
    }

    @Test
    public void testMissingMandatoryStartRejectedByXDef() {
        // wf.xdef 中 start 为 mandatory 节点，缺失时应在 xdef 校验期即被拒绝
        XNode node = XNodeParser.instance().parseFromText(null,
                "<workflow x:schema=\"/nop/schema/wf/wf.xdef\" xmlns:x=\"/nop/schema/xdsl.xdef\">"
                        + "<steps><step name=\"wf-start\"><transition><to-end/></transition></step></steps>"
                        + "</workflow>");
        try {
            WfModel model = (WfModel) new DslModelParser(NopWfCoreConstants.XDEF_PATH_WF)
                    .parseFromNode(node);
            fail("expect missing start rejected, but got " + model);
        } catch (NopException e) {
            assertNotNull(e.getErrorCode());
        }
    }
}
