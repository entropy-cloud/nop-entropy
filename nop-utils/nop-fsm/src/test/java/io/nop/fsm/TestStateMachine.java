package io.nop.fsm;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.fsm.execution.StateMachine;
import io.nop.fsm.model.StateModel;
import io.nop.fsm.model.StateTransitionModel;
import io.nop.fsm.model.StateMachineModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.fsm.FsmErrors.ERR_FSM_DUPLICATE_STATE_VALUE;
import static io.nop.fsm.FsmErrors.ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT;
import static io.nop.fsm.FsmErrors.ERR_FSM_UNDEFINED_STATE;
import static io.nop.fsm.FsmErrors.ERR_FSM_UNKNOWN_STATE_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.2-005（plan 2407）：StateMachine 转移语义直接测试——命中/未命中/ignore/未知 state、
 * 动作精确顺序（IEvalAction 与 IEvalFunction 两类 recording）、模型校验真实面
 * （Validator 校验 initial/子 initial/onDone/onError，不校验 transition target——
 * 悬空 target 为运行时 requireState 行为）、重复 stateValue 拒绝。
 * 全部程序化构造模型，无 VFS/DB 依赖。
 */
public class TestStateMachine {
    private final IEvalContext evalCtx = () -> null;

    private static StateTransitionModel transition(String id, String event, String target) {
        StateTransitionModel t = new StateTransitionModel();
        t.setId(id);
        t.setEvent(event);
        t.setTarget(target);
        return t;
    }

    private static StateModel state(String id, String stateValue, StateTransitionModel... transitions) {
        StateModel s = new StateModel();
        s.setId(id);
        if (stateValue != null)
            s.setStateValue(stateValue);
        if (transitions.length > 0)
            s.setTransitions(Arrays.asList(transitions));
        return s;
    }

    private static StateMachineModel model(StateModel... states) {
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial(states[0].getId());
        stm.setStateProp("state");
        stm.setStates(Arrays.asList(states));
        stm.init();
        return stm;
    }

    private static StateMachine machine(StateMachineModel stm) {
        return new StateMachine(stm);
    }

    /** BiConsumer 记录器：记录 target state 的 stateValue。 */
    private static List<Object> recorder() {
        return new ArrayList<>();
    }

    @Test
    public void testHitTransitionChangesStateValue() {
        StateMachineModel stm = model(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", "s2v"));
        List<Object> changes = recorder();
        machine(stm).transit("s1v", "go", evalCtx, (stateModel, value) -> changes.add(value));

        assertEquals(List.of("s2v"), changes, "hit transition must move to target stateValue");
    }

    @Test
    public void testNoTransitionThrowsWithParams() {
        StateMachineModel stm = model(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", "s2v"));

        NopException ex = assertThrows(NopException.class,
                () -> machine(stm).transit("s1v", "unknown", evalCtx, (s, v) -> {
                }));
        assertEquals(ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT.getErrorCode(), ex.getErrorCode());
        assertEquals("s1", ex.getParam("stateId"));
        assertEquals("unknown", ex.getParam("event"));
    }

    @Test
    public void testIgnoreUnknownTransitionKeepsState() {
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("s1");
        stm.setStateProp("state");
        stm.setIgnoreUnknownTransition(true);
        stm.setStates(Arrays.asList(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", "s2v")));
        stm.init();

        List<Object> changes = recorder();
        machine(stm).transit("s1v", "unknown", evalCtx, (s, v) -> changes.add(v));
        assertTrue(changes.isEmpty(), "ignored transition must not change state");
    }

    @Test
    public void testUnknownStateValueThrows() {
        StateMachineModel stm = model(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", "s2v"));

        NopException ex = assertThrows(NopException.class,
                () -> machine(stm).transit("nope", "go", evalCtx, (s, v) -> {
                }));
        assertEquals(ERR_FSM_UNKNOWN_STATE_VALUE.getErrorCode(), ex.getErrorCode());
        assertEquals("nope", ex.getParam("stateValue"));
    }

    @Test
    public void testInitStateAndInitValueFallbackToFullStateId() {
        // s1 显式 stateValue；s2 无 stateValue——fallback 为 fullStateId 字符串 "s2"
        StateMachineModel stm = model(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", null));

        assertEquals("s1v", machine(stm).getInitStateValue());

        StateMachineModel stm2 = model(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", null));
        ((StateModel) stm2.getStates().get(1)).setStateValue(null);
        stm2.setInitial("s2");
        stm2.init();
        assertEquals("s2", machine(stm2).getInitStateValue(), "missing stateValue falls back to fullStateId");

        Map<String, Object> bean = new HashMap<>();
        machine(stm).initState(bean);
        assertEquals("s1v", bean.get("state"), "initState must write initial stateValue via stateProp");
    }

    @Test
    public void testNestedChildStateTransition() {
        // parent p(initial=c1) 含子 c1 --go--> c2（c2 亦为 p 的子状态：target 在当前状态的 parent container 解析）
        StateModel c1 = state("c1", "c1v", transition("t1", "go", "c2"));
        StateModel c2 = state("c2", "c2v");
        StateModel p = state("p", "pv");
        p.setInitial("c1");
        p.setStates(Arrays.asList(c1, c2));

        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("p");
        stm.setStateProp("state");
        stm.setStates(List.of(p));
        stm.init();

        List<Object> changes = recorder();
        machine(stm).transit("c1v", "go", evalCtx, (s, v) -> changes.add(v));
        assertEquals(List.of("c2v"), changes, "transition from nested child must land on sibling child");
    }

    @Test
    public void testActionExactOrder() {
        // 两类动作承载：invoke/onExit/onEntry 为 IEvalAction；exit/transition actions 为字符串集，
        // 经 model.invokeAction(IEvalFunction) 分发（M2 修订）
        List<String> order = new ArrayList<>();

        StateModel s1 = state("s1", "s1v");
        s1.setOnExit((IEvalAction) ctx -> {
            order.add("onExit");
            return null;
        });
        s1.setExit(new LinkedHashSet<>(List.of("exitAction")));

        StateModel s2 = state("s2", "s2v");
        s2.setOnEntry((IEvalAction) ctx -> {
            order.add("onEntry");
            return null;
        });

        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("s1");
        stm.setStateProp("state");
        stm.setInvokeAction((IEvalFunction) (thisObj, args, scope) -> {
            order.add("fn:" + args[0]);
            return null;
        });
        StateTransitionModel t1 = transition("t1", "go", "s2");
        // transition.invoke 是 IEvalAction 承载（doTransit 首个动作）
        t1.setInvoke((IEvalAction) ctx -> {
            order.add("invoke");
            return null;
        });
        t1.setActions(new LinkedHashSet<>(List.of("transAction")));
        s1.setTransitions(List.of(t1));
        stm.setStates(Arrays.asList(s1, s2));
        stm.init();

        machine(stm).transit("s1v", "go", evalCtx, (s, v) -> {
        });

        assertEquals(List.of("invoke", "onExit", "fn:exitAction", "fn:transAction", "onEntry"), order,
                "exact action order: invoke -> onExit -> exit-set -> transition-actions -> onEntry");
    }

    @Test
    public void testValidatorDanglingMachineInitial() {
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("ghost");
        stm.setStateProp("state");
        stm.setStates(List.of(state("s1", "s1v")));

        NopException ex = assertThrows(NopException.class, stm::init);
        assertEquals(ERR_FSM_UNDEFINED_STATE.getErrorCode(), ex.getErrorCode());
        assertEquals("ghost", ex.getParam("stateId"));
    }

    @Test
    public void testValidatorDanglingSubInitialOnDoneOnError() {
        // Validator 仅在机器 initial 非空时才进入 validateStates（Validator:21-27）——须先设合法 initial
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("s1");
        stm.setStateProp("state");
        StateModel s1 = state("s1", "s1v");
        s1.setInitial("ghost");
        stm.setStates(List.of(s1));
        NopException ex = assertThrows(NopException.class, stm::init);
        assertEquals(ERR_FSM_UNDEFINED_STATE.getErrorCode(), ex.getErrorCode());
        assertEquals("ghost", ex.getParam("stateId"));

        StateModel s2 = state("s2", "s2v");
        s2.setOnDone("ghost");
        StateMachineModel stm2 = new StateMachineModel();
        stm2.setInitial("s2");
        stm2.setStateProp("state");
        stm2.setStates(List.of(s2));
        ex = assertThrows(NopException.class, stm2::init);
        assertEquals(ERR_FSM_UNDEFINED_STATE.getErrorCode(), ex.getErrorCode());
        assertEquals("ghost", ex.getParam("stateId"));

        StateModel s3 = state("s3", "s3v");
        s3.setOnError("ghost");
        StateMachineModel stm3 = new StateMachineModel();
        stm3.setInitial("s3");
        stm3.setStateProp("state");
        stm3.setStates(List.of(s3));
        ex = assertThrows(NopException.class, stm3::init);
        assertEquals(ERR_FSM_UNDEFINED_STATE.getErrorCode(), ex.getErrorCode());
        assertEquals("ghost", ex.getParam("stateId"));
    }

    @Test
    public void testRuntimeDanglingTransitionTargetThrows() {
        // B1 修订：Validator 不校验 transition target——悬空 target 是运行时 requireState 行为
        StateTransitionModel t1 = transition("t1", "go", "ghost");
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("s1");
        stm.setStateProp("state");
        stm.setStates(List.of(state("s1", "s1v", t1), state("s2", "s2v")));
        stm.init();

        NopException ex = assertThrows(NopException.class,
                () -> machine(stm).transit("s1v", "go", evalCtx, (s, v) -> {
                }));
        assertEquals(ERR_FSM_UNDEFINED_STATE.getErrorCode(), ex.getErrorCode());
        assertEquals("ghost", ex.getParam("stateId"));
    }

    @Test
    public void testDuplicateStateValueRejected() {
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("s1");
        stm.setStateProp("state");
        stm.setStates(Arrays.asList(state("s1", "dup"), state("s2", "dup")));

        NopException ex = assertThrows(NopException.class, stm::init);
        assertEquals(ERR_FSM_DUPLICATE_STATE_VALUE.getErrorCode(), ex.getErrorCode());
        assertEquals("dup", ex.getParam("stateValue"));
    }

    @Test
    public void testStateValueTypeNormalization() {
        // stateValueType=STRING：stateValue 经 StdDataType 归一——transit 传入等价原值即可命中
        StateMachineModel stm = new StateMachineModel();
        stm.setInitial("s1");
        stm.setStateProp("state");
        stm.setStateValueType(StdDataType.STRING);
        stm.setStates(Arrays.asList(
                state("s1", "s1v", transition("t1", "go", "s2")),
                state("s2", "s2v")));
        stm.init();

        List<Object> changes = recorder();
        machine(stm).transit("s1v", "go", evalCtx, (s, v) -> changes.add(v));
        assertEquals(List.of("s2v"), changes);
    }
}
