package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2496 4.5-C4-001 回归：run --interval watch 周期链接入平台绝缘设施——单轮脚本异常
 * （含 file.listFiles() 瞬时 null→NPE 形态）后 watch 轮询存活且失败可见（返回 future 异常终态 +
 * 失败日志），不再「静默停摆且进程挂在 stdin」。
 */
public class TestCliRunWatchInsulation {

    private final List<Future<?>> watchFutures = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Future<?> f : watchFutures) {
            f.cancel(false);
        }
        watchFutures.clear();
    }

    static class FailingRunCommand extends CliRunCommand {
        final AtomicInteger rounds = new AtomicInteger();

        @Override
        protected void runTasks(Map<String, Object> globalState) {
            rounds.incrementAndGet();
            throw new NopException(io.nop.cli.CliErrors.ERR_CLI_FILE_NOT_TASK_FILE)
                    .param(io.nop.cli.CliErrors.ARG_PATH, "watch-boom.xrun");
        }
    }

    /**
     * C4-001 主用例：单轮失败后 watch 链存活（多轮触发），且失败可见（future 异常终态）。
     */
    @Test
    void testWatchChainSurvivesScriptFailureAndFailureIsVisible() throws Exception {
        FailingRunCommand command = new FailingRunCommand();
        command.file = new java.io.File("unused.xrun");
        command.interval = 50;

        CompletableFuture<?> watchFuture = (CompletableFuture<?>) command.startWatchLoop(new ConcurrentHashMap<>());
        watchFutures.add(watchFuture);

        long deadline = System.currentTimeMillis() + 10_000;
        // 失败轮次按设施退避节奏重臂（下限 1s）：等到至少 2 轮触发即证明链存活
        while (command.rounds.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertTrue(command.rounds.get() >= 2,
                "C4-001: watch chain must survive script failure rounds (re-arm), rounds="
                        + command.rounds.get());
        assertTrue(watchFuture.isCompletedExceptionally(),
                "C4-001: failure must be visible on the returned future (no silent discard)");
    }

    /**
     * 默认不动 watch 链的稳定路径：runTasks 正常返回时链持续多轮、future 不异常完结。
     */
    @Test
    void testHealthyWatchChainKeepsRunningWithoutFailureTerminal() throws Exception {
        FailingRunCommand command = new FailingRunCommand() {
            @Override
            protected void runTasks(Map<String, Object> globalState) {
                rounds.incrementAndGet();
            }
        };
        command.file = new java.io.File("unused.xrun");
        command.interval = 50;

        CompletableFuture<?> watchFuture = (CompletableFuture<?>) command.startWatchLoop(new ConcurrentHashMap<>());
        watchFutures.add(watchFuture);

        long deadline = System.currentTimeMillis() + 10_000;
        while (command.rounds.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(command.rounds.get() >= 3, "healthy watch chain keeps triggering");
        assertTrue(!watchFuture.isCompletedExceptionally(),
                "healthy chain must not settle exceptionally");
    }
}
