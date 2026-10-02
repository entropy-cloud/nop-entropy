package io.nop.wf.core.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: ApprovalFlowHelper 安全语义测试。
 * autoTransit 的死循环保护（ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT）与
 * cancelSteps 的 CANCELLED 退出语义。
 */
public class TestApprovalFlowHelper {
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    @Test
    public void testAutoTransitLoopGuardThrows() {
        // runAutoTransitions 恒返回 true（模拟恒真的回退迁移条件），必须被上限保护拦截
        IWorkflow wf = proxy(IWorkflow.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "runAutoTransitions":
                    return true;
                case "getWfId":
                    return "wf-loop";
                default:
                    return InvocationHandler.invokeDefault(proxy, method, args);
            }
        });

        NopException e = assertThrows(NopException.class, () -> ApprovalFlowHelper.autoTransit(wf, null));
        assertEquals(ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testAutoTransitStopsWhenNoMoreTransitions() {
        List<Integer> calls = new ArrayList<>();
        IWorkflow wf = proxy(IWorkflow.class, (proxy, method, args) -> {
            if ("runAutoTransitions".equals(method.getName())) {
                calls.add(1);
                return calls.size() <= 2;
            }
            return InvocationHandler.invokeDefault(proxy, method, args);
        });

        ApprovalFlowHelper.autoTransit(wf, null);
        // 迁移至无更多自动迁移为止
        assertEquals(3, calls.size());
    }

    @Test
    public void testCancelStepsExitsWithCancelledStatus() {
        List<Integer> exitStatuses = new ArrayList<>();
        IWorkflowStep step = proxy(IWorkflowStep.class, (proxy, method, args) -> {
            if ("exitStep".equals(method.getName())) {
                exitStatuses.add((Integer) args[0]);
                return null;
            }
            return InvocationHandler.invokeDefault(proxy, method, args);
        });

        ApprovalFlowHelper.cancelSteps(List.of(step), null);

        assertEquals(1, exitStatuses.size());
        assertEquals(NopWfCoreConstants.WF_STEP_STATUS_CANCELLED, exitStatuses.get(0));
    }

    @Test
    public void testAgreeInvokesAgreeActionAndAutoTransits() {
        List<String> invoked = new ArrayList<>();
        List<Integer> transits = new ArrayList<>();
        IWorkflowStep step = proxy(IWorkflowStep.class, (proxy, method, args) -> {
            if ("invokeAction".equals(method.getName())) {
                invoked.add((String) args[0]);
                return null;
            }
            if ("getWorkflow".equals(method.getName())) {
                return proxy(IWorkflow.class, (p2, m2, a2) -> {
                    if ("runAutoTransitions".equals(m2.getName())) {
                        transits.add(1);
                        return false;
                    }
                    return InvocationHandler.invokeDefault(p2, m2, a2);
                });
            }
            return InvocationHandler.invokeDefault(proxy, method, args);
        });

        ApprovalFlowHelper.agree(step, null, null);
        ApprovalFlowHelper.disagree(step, null, null);

        // agree/disagree 分别触发对应 action，且各做一次自动迁移收敛
        assertEquals(List.of(NopWfCoreConstants.ACTION_AGREE, NopWfCoreConstants.ACTION_DISAGREE), invoked);
        assertEquals(2, transits.size());
        assertTrue(transits.get(0) == 1 && transits.get(1) == 1);
    }
}
