package io.nop.xlang.debug.web;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.json.JsonTool;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.DebugExpressionExecutor;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.DebugSessionScope;
import io.nop.xlang.debugger.session.SessionRoutingExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * item 21 示例脚本调试全链（plan 2475）：真实 XDSL 资源（DebugDemoTask 的 xpl step）
 * + 真实 task 引擎线程，经 WS 调试网关全链——握手→session.open→setBreakpoints（真实
 * 资源位置）→业务线程执行→evt.suspended→求值→stepOver→resume→任务完成→session.close。
 * 与 TestDebugGatewayE2E 的差异：彼处断点位置为合成值、脚本线程为桩；本测试的挂起
 * 即 task step 编译链经全局执行器路由到会话 debugger 的运行时证据（接线验证）。
 */
public class TestXdslTaskDebugFullChain extends AbstractDebugGatewayTest {
    private static final ITaskFlowManager taskManager = new TaskFlowManagerImpl();
    private static IExpressionExecutor prevExecutor;

    @BeforeAll
    static void initPlatform() {
        // 网关基座 setUp 不含平台初始化：task 模型加载（VFS/registry）需要 CoreInitialization
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyPlatform() {
        CoreInitialization.destroy();
    }

    static class RecordingDebugger extends XLangDebugger {
        final List<SourceLocation> statementLocs = new ArrayList<>();

        @Override
        public void checkBreakpoint(SourceLocation loc, EvalRuntime rt) {
            if (loc != null)
                statementLocs.add(loc);
            super.checkBreakpoint(loc, rt);
        }
    }

    private static void restoreExecutor() {
        if (prevExecutor != null && !(prevExecutor instanceof DefaultExpressionExecutor))
            EvalExprProvider.registerGlobalExecutor(prevExecutor);
        else
            EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
    }

    /** 无会话跑一次 task（inputs a/b 经 scope 绑定），返回 syncGetOutputs。 */
    private static Map<String, Object> runTask(int a, int b) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("a", a);
        inputs.put("b", b);
        IEvalScope scope = XLang.newEvalScope(inputs);
        ITask task = taskManager.getTask("DebugDemoTask", 0);
        ITaskRuntime taskRt = taskManager.newTaskRuntime(task, false, null, scope);
        return task.execute(taskRt).syncGetOutputs();
    }

    /** record pass：直连 DebugExpressionExecutor（不经会话路由）收集 xpl step 语句位置。 */
    private List<SourceLocation> recordStatementLocations() {
        RecordingDebugger recorder = new RecordingDebugger();
        EvalExprProvider.registerGlobalExecutor(new DebugExpressionExecutor(recorder));
        try {
            Map<String, Object> out = runTask(2, 3);
            assertEquals(7, ((Number) out.get("sum")).intValue());
        } finally {
            restoreExecutor();
        }
        assertFalse(recorder.statementLocs.isEmpty(),
                "task xpl step 必须经过调试 choke point（接线验证：checkBreakpoint 携真实资源位置）");
        // closure audit m2：字面量锚定——防 record 与事件两处一致性地漂移成同错互证
        assertEquals("/nop/task/DebugDemoTask/v1.task.xml", recorder.statementLocs.get(0).getPath());
        return recorder.statementLocs;
    }

    /**
     * 全链（plan 2475）：open→setBreakpoints（真实位置）→task 执行→挂起→求值→单步→resume→完成→close。
     * 单步粒度 = task step 边界（live 实证：task xpl step 编译为单函数体 __fn_1，
     * checkBreakpoint 每次执行仅入口一次——step 内语句级单步不经执行器路由；
     * 以双 xpl step 任务承载"单步到下一执行单元"语义）。
     */
    @Test
    public void testTaskXplStepFullDebugChain() throws Exception {
        List<SourceLocation> stmtLocs = recordStatementLocations();
        assertTrue(stmtLocs.size() >= 2, "双 xpl step 应产生两个可断点执行入口（step 边界粒度）");
        SourceLocation step1Loc = stmtLocs.get(0);

        registerRoutingExecutor();
        try {
            runFullChain(step1Loc);
        } finally {
            restoreExecutor();
        }
    }

    private void runFullChain(SourceLocation step1Loc) throws Exception {
        MockWsSession ws = new MockWsSession();
        DebugWebSocketHandler handler = newHandler(ws, TOK_U1);
        String sessionId = openSession(handler, ws, "1");
        XLangDebugger debugger = debuggerOf(sessionId);
        // 断点位置按虚拟路径匹配：外部路径解析会使 record 位置永不匹配（audit M5）
        debugger.setUseExternalPath(false);

        Map<String, Object> bpParam = Map.of("bps", List.of(
                Map.of("sourcePath", step1Loc.getPath(), "line", step1Loc.getLine())));
        handler.onMessage(req("2", "debugger.setBreakpoints", JsonTool.stringify(bpParam)));
        assertNotNull(findResponse(ws.snapshot(), "2"), "setBreakpoints 应答");

        // 业务线程执行真实 task——挂起点即 task 编译链接线的运行时证据
        AtomicReference<Map<String, Object>> outputsRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread business = new Thread(() -> {
            try {
                outputsRef.set(DebugSessionScope.withSession(sessionId, () -> runTask(2, 3)));
            } catch (Throwable t) {
                error.set(t);
            }
        });
        business.start();

        awaitSuspended(debugger);
        awaitUntil("evt.suspended 应出站", () -> hasEvent(ws.snapshot(), "evt.suspended"));
        Map<String, Object> suspended = lastEvent(ws.snapshot(), "evt.suspended");
        Map<String, Object> suspendedParams = castMap(suspended.get("params"));
        Map<String, Object> location = castMap(suspendedParams.get("location"));
        assertEquals(step1Loc.getPath(), location.get("sourcePath"), "挂起位置应为真实资源路径");
        assertEquals(step1Loc.getLine(), ((Number) location.get("line")).intValue(), "挂起行应为 step1 入口行");
        long threadId = ((Number) suspendedParams.get("threadId")).longValue();

        // step1 入口挂起：输入 a 可见（useParentScope）
        handler.onMessage(req("3", "debugger.getExprValue",
                "{\"threadId\":" + threadId + ",\"frameIndex\":0,\"expr\":\"a\"}"));
        Map<String, Object> evalResp = findResponse(ws.snapshot(), "3");
        assertNotNull(evalResp, "getExprValue 应答");
        assertEquals("2", evalValue(evalResp), "挂起帧内输入 a == 2");

        // 单步（stepInto，step 边界粒度）→ step2 入口挂起，calc1 已完成（x == 4）
        handler.onMessage(req("4", "debugger.stepInto", "{}"));
        awaitUntil("stepInto 后应再次挂起（step2 入口）", () -> suspendedEvents(ws.snapshot()).size() >= 2);
        Map<String, Object> secondSuspend = suspendedEvents(ws.snapshot()).get(1);
        Map<String, Object> secondParams = castMap(secondSuspend.get("params"));
        assertEquals("stepInto", secondParams.get("reason"));
        Map<String, Object> secondLoc = castMap(secondParams.get("location"));
        assertEquals(step1Loc.getPath(), secondLoc.get("sourcePath"));
        assertTrue(((Number) secondLoc.get("line")).intValue() != step1Loc.getLine(),
                "单步应到达不同执行单元（step2 入口行）");

        handler.onMessage(req("5", "debugger.getExprValue",
                "{\"threadId\":" + threadId + ",\"frameIndex\":0,\"expr\":\"RESULT\"}"));
        assertEquals("4", evalValue(findResponse(ws.snapshot(), "5")), "单步后 step1 返回值 RESULT == 4");

        // resume → task 完成，输出经 useParentScope 汇总
        handler.onMessage(req("6", "debugger.resume", "{}"));
        business.join(15000);
        assertFalse(business.isAlive(), "resume 后业务线程应执行完");
        assertNull(error.get(), () -> String.valueOf(error.get() == null ? null : error.get()));
        assertEquals(7, ((Number) outputsRef.get().get("sum")).intValue(), "task 输出 sum == a*2+b");
        awaitUntil("evt.resumed 应出站", () -> hasEvent(ws.snapshot(), "evt.resumed"));

        handler.onMessage(req("7", "session.close", "{}"));
        awaitUntil("sessionClosed 应出站", () -> hasEvent(ws.snapshot(), "evt.sessionClosed"));
    }

    /** 全局槽位注册路由执行器——调用方必须在 finally 中 restoreExecutor()（防污染同 JVM 其他测试）。 */
    private void registerRoutingExecutor() {
        prevExecutor = EvalExprProvider.getGlobalExecutor();
        EvalExprProvider.registerGlobalExecutor(new SessionRoutingExecutor(manager));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<Map<String, Object>> suspendedEvents(List<String> frames) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String frame : frames) {
            Map<String, Object> msg = parse(frame);
            if ("evt.suspended".equals(msg.get("method")))
                result.add(msg);
        }
        return result;
    }

    private static Map<String, Object> lastEvent(List<String> frames, String method) {
        List<Map<String, Object>> events = suspendedEvents(frames);
        return events.get(events.size() - 1);
    }

    /** getExprValue 结果 = {v: DebugVariable}，value 为字符串化值。 */
    private static String evalValue(Map<String, Object> evalResp) {
        Map<String, Object> result = resultMap(evalResp);
        Map<String, Object> v = castMap(result.get("v"));
        return String.valueOf(v.get("value"));
    }
}
