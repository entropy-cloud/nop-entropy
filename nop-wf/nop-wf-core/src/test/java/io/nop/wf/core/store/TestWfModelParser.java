package io.nop.wf.core.store;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.model.WfActionModel;
import io.nop.wf.core.model.WfJoinStepModel;
import io.nop.wf.core.model.WfJoinType;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfSplitType;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.model.WfStepType;
import io.nop.wf.core.model.WfSubFlowModel;
import io.nop.wf.core.model.WfTransitionModel;
import io.nop.wf.core.model.WfTransitionToModel;
import io.nop.wf.core.model.WfTransitionToType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: WfModelParser 解析语义测试。断言 wf.xdef 模型解析产物的字段映射、
 * WfModelAnalyzer 计算出的图结构不变量（DAG、transitionTo/From、endable 传播、join 祖先推导）。
 */
public class TestWfModelParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private WfModel parse(String path) {
        IResource resource = getResource(path);
        assertNotNull(resource, "resource not found in test vfs: " + path);
        assertTrue(resource.exists(), "resource does not exist: " + path);
        WfModel wfModel = WfModelParser.parseWorkflowModel(resource);
        assertNotNull(wfModel);
        return wfModel;
    }

    @Test
    public void testParseSimpleFlowBuildsDagAndGraph() {
        WfModel model = parse("/nop/wf/test/parserSimple/v1.xwf");

        // 文件名带数字版本号时从路径推导 wfName/wfVersion
        assertEquals("test/parserSimple", model.getWfName());
        assertEquals(1L, model.getWfVersion());

        assertEquals("wf-start", model.getStart().getStartStepName());
        assertEquals(2, model.getSteps().size());
        assertNotNull(model.getEnd());
        assertNotNull(model.getStartStep());
        assertNotNull(model.getDag(), "analyze之后模型应携带DAG");

        WfStepModel start = model.getStep("wf-start");
        WfStepModel step1 = model.getStep("step1");

        assertEquals(WfStepType.step, start.getStepType());
        assertEquals(Set.of("step1"), start.getTransitionToStepNames());
        assertEquals(Set.of("wf-start"), step1.getTransitionFromStepNames());

        // endable 语义：直接 to-end 的步骤与其全部上游步骤最终都能到达结束
        assertTrue(step1.isNextToEnd());
        assertTrue(step1.isEventuallyToEnd());
        assertTrue(start.isEventuallyToEnd());

        // stepIndex 由 DAG 拓扑序分配，上游小于下游
        assertTrue(start.getStepIndex() < step1.getStepIndex());
    }

    @Test
    public void testParseSpecialTransitionTargets() {
        WfModel model = parse("/nop/wf/test/parserSpecial/v1.xwf");
        WfStepModel start = model.getStep("wf-start");

        WfTransitionModel transition = start.getTransition();
        assertNotNull(transition);
        // 未显式配置 splitType 时模型字段保持 null（xdef 缺省值 and 在运行期生效）
        assertNull(transition.getSplitType());
        assertEquals(Set.of("step1"), start.getTransitionToStepNames());

        // 三类特殊迁移目标在解析后体现为步骤标志位
        assertTrue(start.isNextToEnd());
        assertTrue(start.isNextToEmpty());
        assertTrue(start.isNextToAssigned());

        WfActionModel aEnd = (WfActionModel) start.getAction("aEnd");
        assertNotNull(aEnd);
        assertEquals(WfTransitionToType.TO_END, aEnd.getTransition().getTransitionTos().get(0).getType());
        assertEquals(WfTransitionToType.TO_EMPTY,
                ((WfActionModel) start.getAction("aEmpty")).getTransition().getTransitionTos().get(0).getType());
        assertEquals(WfTransitionToType.TO_ASSIGNED,
                ((WfActionModel) start.getAction("aAssigned")).getTransition().getTransitionTos().get(0).getType());

        // to-empty/to-assigned/to-end 均可让步骤最终结束
        assertTrue(start.isEventuallyToEnd());

        // 枚举文本映射
        assertEquals(WfTransitionToType.TO_END, WfTransitionToType.fromText("to-end"));
        assertEquals("to-empty", WfTransitionToType.TO_EMPTY.getText());
        assertNull(WfTransitionToType.fromText("unknown-target"));
    }

    @Test
    public void testParseJoinStepDerivesWaitSteps() {
        WfModel model = parse("/nop/wf/test/parserJoin/v1.xwf");

        WfStepModel joinStep = model.getStep("join");
        assertTrue(joinStep instanceof WfJoinStepModel);
        WfJoinStepModel join = (WfJoinStepModel) joinStep;

        assertEquals(WfStepType.join, joinStep.getStepType());
        // 未显式配置 joinType 时缺省为 and
        assertEquals(WfJoinType.and, join.getJoinType());
        // joinType=and 且未配置 waitStepNames 时，自动推导为 DAG 中的全部祖先
        assertEquals(Set.of("wf-start"), join.getWaitStepNames());

        assertEquals(Set.of("join"), model.getStep("wf-start").getTransitionToStepNames());
        assertTrue(join.isNextToEnd());
    }

    @Test
    public void testParseCommonActionInjectedIntoSteps() {
        WfModel model = parse("/nop/wf/test/parserCommonAction/v1.xwf");

        WfStepModel start = model.getStep("wf-start");
        // 步骤自身 ref 的 sh + 全局注入的 common action
        assertEquals(2, start.getActions().size());
        assertNotNull(start.getAction("sh"));
        assertNotNull(start.getAction("_rejectAction"));

        WfActionModel reject = model.getAction("_rejectAction");
        assertTrue(reject.isCommon());
        assertTrue(reject.isForReject());

        // common action 的 to-assigned 迁移让步骤具备 to-assigned 出边
        assertTrue(start.isNextToAssigned());
        assertTrue(start.isEventuallyToAssigned());
        // sh 的 to-end 保证可结束
        assertTrue(start.isEventuallyToEnd());
    }

    @Test
    public void testParseModelMetadata() {
        WfModel model = parse("/nop/wf/test/parserMeta/v1.xwf");

        // 显式声明的 wfName/wfVersion 优先于路径推导
        assertEquals("meta-model", model.getWfName());
        assertEquals(3L, model.getWfVersion());
        assertEquals("元数据解析", model.getDisplayName());
        assertEquals(200, model.getPriority());
        assertEquals("custom", model.getWfGroup());
        assertEquals("state", model.getBizEntityStateProp());
        assertEquals("flowId", model.getBizEntityFlowIdProp());
        assertTrue(model.getTagSet().contains("a"));
        assertTrue(model.getTagSet().contains("b"));
        assertEquals("用于测试模型元数据解析", model.getDescription());

        assertEquals(2, model.getAuths().size());
        assertTrue(model.getAuth("auth1").isAllowStart());
        assertFalse(model.getAuth("auth1").isAllowEdit());
        assertTrue(model.getAuth("auth2").isAllowEdit());
        assertTrue(model.getAuth("auth2").isAllowManage());

        assertTrue(model.hasListener("l1"));
        assertEquals("wf-start:*", model.getListener("l1").getEventPattern());

        assertNotNull(model.getAction("sh").getArg("opinion"));
        assertNull(model.getAction("sh").getArg("notExist"));
    }

    @Test
    public void testParseSubFlowStep() {
        WfModel model = parse("/nop/wf/test/parserSubFlow/v1.xwf");

        WfStepModel sub = model.getStep("sub");
        assertEquals(WfStepType.flow, sub.getStepType());
        assertTrue(sub instanceof WfSubFlowModel);

        WfSubFlowModel flow = (WfSubFlowModel) sub;
        assertEquals("test/subwf", flow.getStart().getWfName());
        assertEquals(1L, flow.getStart().getWfVersion());
        assertNotNull(flow.getStart().getArg("bizId"));
        assertNotNull(flow.getStart().getReturn("subResult"));
        assertNull(flow.getStart().getReturn("notExist"));

        // 普通步骤与 flow 步骤类型区分
        assertEquals(WfStepType.step, model.getStep("wf-start").getStepType());
    }

    @Test
    public void testParseSplitTypeOrWithOrderedTargets() {
        WfModel model = parse("/nop/wf/test/parserSplitOr/v1.xwf");

        WfActionModel route = model.getAction("route");
        WfTransitionModel transition = route.getTransition();
        assertEquals(WfSplitType.or, transition.getSplitType());

        assertEquals(2, transition.getTransitionTos().size());
        WfTransitionToModel first = transition.getTransitionTos().get(0);
        WfTransitionToModel second = transition.getTransitionTos().get(1);
        assertEquals("big", first.getStepName());
        assertEquals(1, first.getOrder());
        assertNotNull(first.getWhen(), "分支条件应编译为谓词");
        assertEquals("small", second.getStepName());
        assertNull(second.getWhen());

        assertEquals(Set.of("big", "small"), model.getStep("wf-start").getTransitionToStepNames());
    }

    @Test
    public void testParseStartArgs() {
        WfModel model = parse("/nop/wf/test/parserStartArgs/v1.xwf");

        assertNotNull(model.getStart().getArg("bizId"));
        assertTrue(model.getStart().getArg("bizId").isMandatory());
        assertTrue(model.getStart().getArg("bizId").isPersist());

        assertNotNull(model.getStart().getArg("comment"));
        assertFalse(model.getStart().getArg("comment").isMandatory());

        assertNull(model.getStart().getArg("unknown"));
    }

    @Test
    public void testBackLinkEdgeIgnoredByLoopDetection() {
        // b -> a 标记 backLink=true：同构于 errLoop 的环在此合法
        WfModel model = parse("/nop/wf/test/parserBackLink/v1.xwf");

        assertFalse(model.isAllowStepLoop());

        WfStepModel a = model.getStep("a");
        WfStepModel b = model.getStep("b");

        assertEquals(Set.of("b"), a.getTransitionToStepNames());
        assertEquals(Set.of("a"), b.getTransitionToStepNames());

        // DAG 中忽略 backLink 边后 a -> b 仍为无环图，两个步骤均可结束
        assertTrue(a.isEventuallyToEnd());
        assertTrue(b.isEventuallyToEnd());
        assertTrue(a.getStepIndex() < b.getStepIndex());
    }
}
