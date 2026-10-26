/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import test.io.entropy.beans.MyCreationLog;

import java.util.concurrent.TimeUnit;

import static io.nop.core.unittest.BaseTestCase.clearTestConfig;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONCURRENT_START;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端链路 1（plan 2507 rule 22）：IoC 并发启动 → 失败注入 → 失败传导 → 容器终态。
 *
 * <p>接线验证（rule 23）：join 必须经真实生产入口执行——本测试以 {@code CoreInitialization
 * .initialize()}（内部走 {@code IocCoreInitializer.initialize()}：appContainer.start() 后
 * 同一 try 界内 awaitStartFinished 有界 join）驱动全链，非测试直调 awaitStartFinished 自证。</p>
 *
 * <p>传导链：join 抛出 → IocCoreInitializer catch 恢复 parentContainer 后重抛 →
 * CoreInitialization.initializeTo catch → destroy → 重抛（NopApplication 零改动，进程面非零退出
 * 由宿主自然传播）。join 侧失败终态收敛：cancel 孤儿图 + stop + 已建 bean 销毁。</p>
 *
 * <p>本类不做 @BeforeAll 全平台初始化：由测试本体驱动 initialize/destroy 完整生命周期
 * （失败轮 + 成功轮），验证失败后无 limbo 残留、平台可重新初始化。</p>
 */
public class TestIocCoreInitializerJoinE2E {

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    public void testConcurrentStartFailurePropagationThroughCoreInitialization() {
        MyCreationLog.reset();
        setTestConfig(CFG_IOC_APP_BEANS_CONCURRENT_START, true);
        setTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT, 30_000);
        setTestConfig(CFG_IOC_APP_BEANS_FILES, "/nop/ioc/test-start-failure-e2e.beans.xml");
        try {
            // 失败轮：concurrent-start 全链启动，失败注入 bean init 抛出 → join 感知 → 异常传播出 initialize()
            RuntimeException e = assertThrowsRuntime(CoreInitialization::initialize);
            assertEquals("simulated-init-failure", TestIocConcurrentStartFailurePropagation.rootCause(e).getMessage(),
                    "启动失败必须经 IocCoreInitializer join 传导出 CoreInitialization.initialize()");

            // join 侧失败终态收敛：前置 bean 已创建、失败后被销毁（stop 可重入验证见容器级测试）
            assertTrue(MyCreationLog.order.contains("create:okBean"), "前置 bean 应已创建");
            assertTrue(MyCreationLog.order.contains("destroy:okBean"), "失败终态必须销毁已建 bean");
        } finally {
            clearTestConfig(CFG_IOC_APP_BEANS_FILES.getName());
            clearTestConfig(CFG_IOC_APP_BEANS_CONCURRENT_START.getName());
            clearTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT.getName());
        }

        // 容器终态：失败后无 limbo 残留——平台可完整重新初始化（第二轮 initialize 成功）
        CoreInitialization.initialize();
        assertTrue(CoreInitialization.isInitialized(), "失败轮之后平台必须可重新初始化");
    }

    private static RuntimeException assertThrowsRuntime(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            return e;
        } catch (Error e) {
            throw e;
        }
        throw new AssertionError("expected RuntimeException from CoreInitialization.initialize()");
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }
}
