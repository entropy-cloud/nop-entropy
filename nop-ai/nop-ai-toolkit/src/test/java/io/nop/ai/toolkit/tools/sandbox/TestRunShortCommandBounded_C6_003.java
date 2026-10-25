package io.nop.ai.toolkit.tools.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CRA 3.2-C6-003（plan 2501 Phase 2，MA-04 docker runShortCommand 挂死族主登记，
 * 含 C4-002 心跳占用面）：runShortCommand 的 drain {@code in.read()} 无超时且先于
 * {@code waitFor(5s)}——docker daemon 挂死形态（辅助进程不退出且管道被其持有）下
 * drain 永久阻塞，执行墙钟超时清理的线程（工具线程或心跳/取消线程）自身被钉死，
 * waitFor 返回值被忽略且超时后无 destroyForcibly 兜底。
 * <p>
 * 修复面（有界形态）：累计时限 drain（available 探测 + 截止期）→ 有界 waitFor →
 * 超时必 {@code destroyForcibly()}；kill 与 rm -f 两分支共用同一有界站点。
 * <p>
 * 注入形态（C10-006 同套，挂死辅助进程注入）：反射直调（私有静态站点，修复前后
 * 同签名可调）wedged 辅助命令（{@code sh -c "echo out; sleep 60"}——输出后持管道
 * 不退出），断言调用预算内有界返回。修复前 drain 永久阻塞（先红）。
 */
public class TestRunShortCommandBounded_C6_003 {

    private static void assumeShAvailable() {
        try {
            new ProcessBuilder("sh", "-c", "true").start().waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("sh not available");
        }
    }

    private static void invokeRunShortCommand(final String[] argv, CountDownLatch done,
                                              AtomicReference<Throwable> error) {
        Thread caller = new Thread(() -> {
            try {
                Method runShort = DockerBashSandbox.class.getDeclaredMethod("runShortCommand", String[].class);
                runShort.setAccessible(true);
                runShort.invoke(null, (Object) argv);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        }, "c6-003-short-command-caller");
        caller.setDaemon(true);
        caller.start();
    }

    /**
     * 挂死辅助进程注入有界返回断言：daemon 挂死形态（echo 后持管道 sleep）下
     * runShortCommand 必须预算内有界返回，清理线程不再被钉死（wallSeconds 契约
     * 不再被绕过）。修复前 drain read 永久阻塞（先红）。
     */
    @Test
    @Timeout(60)
    public void wedgedHelperCommandWithOutputReturnsBounded() throws Exception {
        assumeShAvailable();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        invokeRunShortCommand(new String[]{"/bin/sh", "-c", "echo wedge-out; sleep 60"}, done, error);

        assertTrue(done.await(20, TimeUnit.SECONDS),
                "runShortCommand must return within the short-command budget (pre-fix: the drain read "
                        + "blocks forever on the wedged helper holding the pipe)");
        assertNull(error.get(), "bounded return must not throw: " + error.get());
    }

    /**
     * 静默挂死形态（无输出、不退出）：drain 截止期 + 有界 waitFor + destroyForcibly
     * 兜底后必须返回——waitFor 超时分支不再被忽略。修复前同样永久阻塞（先红同族）。
     */
    @Test
    @Timeout(60)
    public void wedgedSilentHelperCommandReturnsBounded() throws Exception {
        assumeShAvailable();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        invokeRunShortCommand(new String[]{"/bin/sh", "-c", "sleep 60"}, done, error);

        assertTrue(done.await(20, TimeUnit.SECONDS),
                "runShortCommand must return within the budget even with a silent wedged helper");
        assertNull(error.get(), "bounded return must not throw: " + error.get());
    }
}
