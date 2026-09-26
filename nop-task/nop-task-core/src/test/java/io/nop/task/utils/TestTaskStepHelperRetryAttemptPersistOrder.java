package io.nop.task.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 3 [维度05-05]：retry 计数持久点契约——retryAttempt 增量在下一轮执行前落盘。
 *
 * <p>缺陷场景（audit 维度05-05）：fail → increment 仅内存修改，saveState 才持久化。进程在
 * 保存前崩溃（或保存因乐观锁失败）时本轮 attempt 丢失，resume 后重试预算相对 maxRetryCount
 * 被重置。修复：(a) 增量落盘失败的内存/DB 漂移以 LOG.error 标记后抛出（不再静默）；
 * (b) 契约测试锁定"fail 之后、下一轮执行之前 store 已见递增后的 attempt"。
 */
public class TestTaskStepHelperRetryAttemptPersistOrder {

    static final ErrorCode ERR_TEST_TRANSIENT =
            ErrorCode.define("nop.err.test.retry-attempt-persist.transient", "transient failure");

    /** 记录型 runtime：saveState 时记录 store 可见的 attempt，action 执行时记录其入口可见的 attempt。 */
    static class RecordingStepRt extends TestTaskStepHelperRetrySyncReturn.FakeTaskStepRuntime {
        final List<Integer> savedAttempts = new ArrayList<>();
        final List<Integer> actionEntryAttempts = new ArrayList<>();

        RecordingStepRt(ITaskStepState state) {
            super(state);
        }

        @Override
        public void saveState() {
            savedAttempts.add(getState().getRetryAttempt());
        }
    }

    @Test
    public void retryAttempt_incrementPersistedBeforeNextRound() {
        TaskStepStateBean state = new TaskStepStateBean();
        RecordingStepRt stepRt = new RecordingStepRt(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        AtomicInteger executeCount = new AtomicInteger(0);
        List<Integer> attemptAtActionEntry = stepRt.actionEntryAttempts;
        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, () -> {
            int n = executeCount.incrementAndGet();
            attemptAtActionEntry.add(stepRt.getState().getRetryAttempt());
            if (n < 3)
                throw new NopException(ERR_TEST_TRANSIENT);
            return TaskStepReturn.RETURN_RESULT("OK");
        });

        assertEquals("OK", ret.getResult(), "third attempt succeeds");
        assertEquals(3, executeCount.get(), "1 initial + 2 retries");

        // 每次落盘的 attempt 都已递增：save 序列 = [1, 2]
        assertEquals(java.util.List.of(1, 2), stepRt.savedAttempts,
                "each persist must store the incremented attempt (fail -> increment -> save)");

        // 关键契约：第 n 轮 action 入口看到的 attempt 已是上一轮落盘值（增量先落盘、再进入下一轮）。
        // 首轮入口为 null（尚未有任何 attempt）
        assertEquals(java.util.Arrays.asList(null, 1, 2), attemptAtActionEntry,
                "attempt N>1 must observe the persisted increment from the previous round");
        assertTrue(stepRt.savedAttempts.size() == attemptAtActionEntry.size() - 1,
                "persist happens once per failure, before the next action execution");
    }
}
