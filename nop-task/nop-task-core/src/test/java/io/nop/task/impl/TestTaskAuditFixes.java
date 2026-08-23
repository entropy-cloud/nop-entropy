package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.task.TaskErrors.ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-task 报告 P1 系列修复的回归：
 * <ul>
 *   <li>[P1] 步骤 input mandatory 校验条件反转（非空抛异常、空值放行）；</li>
 *   <li>[P1] next 属性被静默忽略（nextStepName 误传 getNextOnError）；</li>
 *   <li>[P1] timeout cancellable 自引用使外部取消无法传播到步骤；</li>
 *   <li>[P1] getDumpValue 对 null 值 NPE；</li>
 *   <li>[P3] DefaultTaskStateStore 的 parentStepPath 记录为祖父路径。</li>
 * </ul>
 */
public class TestTaskAuditFixes {

    private TaskFlowManagerImpl taskFlowManager;
    private static Cancellable externalToken;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("plusOne", new PlusOneStep());
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        taskFlowManager = new TaskFlowManagerImpl();
        externalToken = new Cancellable();
    }

    private Map<String, Object> runTask(String taskName) {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        return task.execute(taskRt).syncGetOutputs();
    }

    /** [P1] mandatory + 非空值：正常执行（修复前反向抛 mandatory-input-not-allow-empty）。 */
    @Test
    public void mandatoryInputWithNonNullValueExecutes() {
        Map<String, Object> ret = runTask("test/mandatory-input-01");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "mandatory input with legal non-empty value must execute normally");
    }

    /** [P1] mandatory + 空值：抛 ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY（修复前被放行）。 */
    @Test
    public void mandatoryInputWithNullValueRejected() {
        ITask task = taskFlowManager.getTask("test/mandatory-input-empty-01", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        NopException err = assertThrows(NopException.class,
                () -> task.execute(taskRt).syncGetOutputs(),
                "mandatory input with null value must be rejected");
        assertEquals(ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY.getErrorCode(), err.getErrorCode());
    }

    /**
     * [P1] next 跳转：a(next=c)→c 跳过 b（RESULT=1→2→3）；修复前 next 被忽略按文档顺序
     * a→b→c 执行（RESULT=4）。
     */
    @Test
    public void nextAttributeIsHonoredOnSuccessPath() {
        Map<String, Object> ret = runTask("test/next-jump-01");
        assertEquals("OK", ret.get(TaskConstants.VAR_RESULT),
                "next='c' must jump a->c skipping b (RESULT=3); pre-fix next was silently ignored (a->b->c, RESULT=4)");
    }

    /** [P1] getDumpValue(null) 返回 null（修复前 NPE）。 */
    @Test
    public void dumpValueOfNullIsNull() {
        assertNull(TaskStepHelper.getDumpValue(null), "dump of null input/output must not NPE");
    }

    /**
     * [P1] timeout：外部 cancelToken 取消必须传播到步骤的 cancellable
     * （修复前 cancellable.append(cancellable) 自引用，外部取消对步骤无响应直到自身超时）。
     */
    @Test
    public void externalCancelPropagatesToTimeoutCancellable() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        AtomicReference<ICancelToken> stepToken = new AtomicReference<>();
        CompletableFuture<Object> never = new CompletableFuture<>();

        TaskStepReturn ret = TaskStepHelper.timeout(60_000, cancellable -> {
            stepToken.set(cancellable);
            taskStarted.countDown();
            return TaskStepReturn.ASYNC(null, never);
        }, externalToken, GlobalExecutors.globalTimer());

        assertTrue(taskStarted.await(5, TimeUnit.SECONDS));
        externalToken.cancel("test-external-cancel");

        assertNotNull(stepToken.get(), "step cancellable must be created");
        assertTrue(stepToken.get().isCancelled(),
                "external cancel must propagate to the step's timeout cancellable");
        never.complete(null);
    }

    /** [P3] DefaultTaskStateStore 的 parentStepPath 应为父步骤自身路径（修复前取祖父路径）。 */
    @Test
    public void parentStepPathIsDirectParentPath() {
        DefaultTaskStateStore store = new DefaultTaskStateStore();
        io.nop.task.ITaskRuntime taskRt = fakeTaskRuntime();

        io.nop.task.ITaskStepState grandParent = store.newStepState(null, "gp", "simple", taskRt);
        io.nop.task.ITaskStepState parent = store.newStepState(grandParent, "p", "simple", taskRt);
        io.nop.task.ITaskStepState child = store.newStepState(parent, "c", "simple", taskRt);

        assertEquals(parent.getStepPath(), child.getParentStepPath(),
                "parentStepPath must be the direct parent's stepPath");
        assertEquals(grandParent.getStepPath(), parent.getParentStepPath());
    }

    static io.nop.task.ITaskRuntime fakeTaskRuntime() {
        return (io.nop.task.ITaskRuntime) java.lang.reflect.Proxy.newProxyInstance(
                io.nop.task.ITaskRuntime.class.getClassLoader(),
                new Class[]{io.nop.task.ITaskRuntime.class},
                (proxy, method, args) -> {
                    if ("newStepInstanceId".equals(method.getName()))
                        return "test-instance";
                    if (method.getReturnType() == boolean.class)
                        return false;
                    if (method.getReturnType() == int.class || method.getReturnType() == long.class)
                        return 0;
                    return null;
                });
    }

    /** 测试用步骤：读取输入 x 并加一返回（输入由 TaskStepExecution.initInputs 写入 stepRt）。 */
    public static class PlusOneStep extends AbstractTaskStep {
        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            Object x = stepRt.getValue("x");
            int v = x == null ? 0 : Integer.parseInt(x.toString());
            return TaskStepReturn.of(null, v + 1);
        }
    }
}
