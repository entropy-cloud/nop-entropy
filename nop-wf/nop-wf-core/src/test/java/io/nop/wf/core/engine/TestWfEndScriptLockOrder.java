package io.nop.wf.core.engine;

import io.nop.api.core.auth.IUserDelegateService;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.core.mock.InMemoryWfStore;
import io.nop.wf.core.mock.SimpleWfActorResolver;
import io.nop.wf.core.store.IWorkflowRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2585 Phase 1 钉测试：
 * <ol>
 * <li>锁序钉：子流程终结→父侧通知链的跨 wf 条带锁嵌套只允许「子→父」单向（缺陷态：
 * 父→子反向嵌套 → ABBA 环 → violation 非空即红）。</li>
 * <li>end 脚本出锁钉：end source 在终态预留（saveWfRecord 落盘 ended 状态）之后执行，
 * 且执行时不持任何 wf 条带锁；同 stripe 并发操作不被阻塞；并发双跑只执行一次脚本。</li>
 * </ol>
 * 模型定义见 src/test/resources/_vfs/nop/wf/test/engineSubFlowParent、engineSubFlowChild、
 * engineEndScript。
 */
public class TestWfEndScriptLockOrder extends BaseTestCase {
    private WorkflowManagerImpl workflowManager;
    private LockTrackingStore store;
    private WorkflowEngineImpl engine;

    /**
     * 锁序跟踪 store：withWfLock 段内登记线程持有 wfId 集合，跨 wf 嵌套取锁记为 violation。
     * plan 2585 后终结通知段应完全不嵌套（violation 为空）。
     */
    static class LockTrackingStore extends InMemoryWfStore {
        final ThreadLocal<ArrayDeque<String>> heldLocks = ThreadLocal.withInitial(ArrayDeque::new);
        final List<String> violations = java.util.Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger terminalSaves = new AtomicInteger();
        volatile boolean terminalSavedUnderLock;

        @Override
        public <T> T withWfLock(String wfId, Supplier<T> action) {
            if (wfId == null)
                return action.get();
            ArrayDeque<String> held = heldLocks.get();
            if (!held.isEmpty()) {
                violations.add(String.join("->", held) + "->" + wfId);
            }
            held.push(wfId);
            try {
                return super.withWfLock(wfId, action);
            } finally {
                held.pop();
            }
        }

        @Override
        public void saveWfRecord(IWorkflowRecord record) {
            if (record.getStatus() != null
                    && record.getStatus() >= io.nop.wf.core.NopWfCoreConstants.WF_STATUS_HISTORY_BOUND) {
                terminalSaves.incrementAndGet();
                if (!heldLocks.get().isEmpty())
                    terminalSavedUnderLock = true;
            }
            super.saveWfRecord(record);
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        workflowManager = new WorkflowManagerImpl();

        engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new SimpleWfActorResolver());
        engine.setUserDelegateService(new IUserDelegateService() {
            @Override
            public boolean canDelegate(String userId, String ownerId, String scope) {
                return false;
            }

            @Override
            public java.util.Set<String> getDelegateOwnerIds(String userId, String scope) {
                return java.util.Collections.emptySet();
            }
        });
        workflowManager.setWorkflowEngine(engine);
        store = new LockTrackingStore();
        workflowManager.setWorkflowStore(store);
        workflowManager.init();
    }

    private IServiceContext ctx(String userId) {
        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId(userId);
        return context;
    }

    private IWorkflowStep step(IWorkflow wf, String stepName) {
        List<IWorkflowStep> found = wf.getSteps(true).stream()
                .filter(s -> s.getStepName().equals(stepName) && !s.isHistory())
                .collect(Collectors.toList());
        if (found.size() != 1)
            throw new AssertionError("expect exactly one active step " + stepName + ", got " + found.size());
        return found.get(0);
    }

    /**
     * 锁序钉：父流程启动子流程 → 子流程终结 → 父侧通知迁移。
     * 断言：(a) 子→父通知路径无任何跨 wf 锁嵌套（缺陷态：子锁内取父锁 → violation 非空）；
     * (b) 子/父流程均恰好终结，父侧子流程步骤完成迁移（功能不回归）。
     */
    @Test
    public void testSubFlowEndNotifyRunsOutsideChildLock() {
        IWorkflow parent = workflowManager.newWorkflow("test/engineSubFlowParent", 1L);
        parent.start(null, ctx("1"));

        step(parent, "wf-start").invokeAction("go", null, ctx("1"));

        // 子流程步骤已启动子流程实例
        IWorkflowStep subStep = step(parent, "sub");
        String childWfId = subStep.getRecord().getSubWfId();
        assertTrue(childWfId != null && !childWfId.isEmpty());

        IWorkflow child = workflowManager.getWorkflow(childWfId);
        assertTrue(!child.isEnded());

        // 子流程终结：触发子 end → endSubFlow → 父侧 notifySubFlowEnd（取父锁）
        step(child, "wf-start").invokeAction("sp", null, ctx("1"));

        assertTrue(child.isEnded(), "child workflow not ended");
        assertTrue(parent.isEnded(), "parent workflow not ended after sub flow completion");

        // 锁序断言（子→父单向不变式）：整个子终结→父通知链中，跨 wf 条带锁嵌套只允许
        // 「持子锁 → 取父锁」方向（TO_END 内联 checkEnd 场景的合法残余）；任何
        // 「持父锁 → 取子锁」反向嵌套都是缺陷（ABBA 环来源）
        for (String violation : store.violations) {
            if (!violation.equals(childWfId + "->" + parent.getWfId())) {
                fail("lock-order violation (only child->parent nesting allowed): " + violation
                        + ", all: " + store.violations);
            }
        }
        // 终态预留必须发生在锁内（防双跑）
        assertTrue(store.terminalSavedUnderLock, "terminal state was saved outside wf lock");
    }

    /**
     * 精简 IWorkflowImplementor 替身：fresh（未冻结）WfModel/WfEndModel + 真实条带锁 store。
     * 仅覆盖 doEndWorkflow 路径所需成员（引擎访问面：store/model/record/globalVars/基础身份）。
     */
    private io.nop.wf.core.impl.IWorkflowImplementor bareWorkflow(
            io.nop.wf.core.store.beans.WorkflowRecordBean record,
            io.nop.wf.core.model.WfModel model, String wfId) {
        return (io.nop.wf.core.impl.IWorkflowImplementor) java.lang.reflect.Proxy.newProxyInstance(
                io.nop.wf.core.impl.IWorkflowImplementor.class.getClassLoader(),
                new Class[]{io.nop.wf.core.impl.IWorkflowImplementor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getStore":
                            return store;
                        case "getModel":
                            return model;
                        case "getRecord":
                            return record;
                        case "getWfId":
                            return wfId;
                        case "getGlobalVars":
                            return store.getGlobalVars(record);
                        case "toString":
                            return "bare-wf[" + wfId + "]";
                        default:
                            if (method.isDefault())
                                return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    /**
     * end 脚本出锁钉：终态预留（锁内落盘）→ 锁外执行 end source（可慢执行，不阻塞同
     * stripe 其他操作）→ AFTER_END。并发双到达只执行一次脚本。
     */
    @Test
    public void testEndScriptRunsOutsideLockWithoutBlockingSameStripe() throws Exception {
        io.nop.wf.core.store.beans.WorkflowRecordBean record =
                new io.nop.wf.core.store.beans.WorkflowRecordBean();
        record.setWfName("test/engineEndScriptBare");
        record.setWfVersion(1L);
        record.setStatus(io.nop.wf.core.NopWfCoreConstants.WF_STATUS_ACTIVATED);
        record.setWfId("wf-end-pin-1");
        store.saveWfRecord(record);

        io.nop.wf.core.model.WfEndModel endModel = new io.nop.wf.core.model.WfEndModel();
        io.nop.wf.core.model.WfModel model = new io.nop.wf.core.model.WfModel();
        model.setEnd(endModel);

        io.nop.wf.core.impl.IWorkflowImplementor wf = bareWorkflow(record, model, "wf-end-pin-1");

        AtomicInteger scriptRuns = new AtomicInteger();
        CountDownLatch scriptStarted = new CountDownLatch(1);
        CountDownLatch releaseScript = new CountDownLatch(1);
        endModel.setSource(scope -> {
            // 断言 1：脚本执行时终态已预留落盘（防双跑窗已关闭）
            assertTrue(record.getStatus() != null && record.getStatus()
                            >= io.nop.wf.core.NopWfCoreConstants.WF_STATUS_HISTORY_BOUND,
                    "end script ran before terminal reservation");
            // 断言 2：脚本执行时不持任何 wf 条带锁
            assertTrue(store.heldLocks.get().isEmpty(),
                    "end script executed while holding wf stripe locks");
            scriptRuns.incrementAndGet();
            scriptStarted.countDown();
            try {
                // 模拟慢脚本：阻塞期间同 stripe 必须可被其他线程进入
                if (!releaseScript.await(20, TimeUnit.SECONDS))
                    fail("script not released in time");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new io.nop.api.core.exceptions.NopException(
                        io.nop.wf.core.NopWfCoreErrors.ERR_WF_INVALID_SCHEDULER_CONFIG, e)
                        .description("interrupted while blocking inside end-script stub");
            }
            return 1;
        });

        WfRuntime wfRt = new WfRuntime(wf, ctx("1"));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> f = pool.submit(() -> engine.doEndWorkflow(
                    io.nop.wf.core.NopWfCoreConstants.WF_STATUS_COMPLETED, wfRt));

            assertTrue(scriptStarted.await(20, TimeUnit.SECONDS), "end script did not start");

            // 断言 3：脚本慢执行期间，同 wfId stripe 上其他操作不被阻塞
            long begin = System.nanoTime();
            store.withWfLock("wf-end-pin-1", () -> null);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
            assertTrue(elapsedMs < 5000, "same-stripe operation blocked by end script");

            releaseScript.countDown();
            f.get(30, TimeUnit.SECONDS);

            // 断言 4：并发/重复终结到达不再重跑脚本（终态预留防双跑）
            engine.doEndWorkflow(io.nop.wf.core.NopWfCoreConstants.WF_STATUS_COMPLETED,
                    new WfRuntime(wf, ctx("1")));
            assertEquals(1, scriptRuns.get(), "end script must run exactly once");
            assertEquals(1, store.terminalSaves.get(), "terminal state must be reserved exactly once");
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE)
            return null;
        if (returnType == boolean.class)
            return false;
        if (returnType == int.class)
            return 0;
        if (returnType == long.class)
            return 0L;
        return null;
    }
}
