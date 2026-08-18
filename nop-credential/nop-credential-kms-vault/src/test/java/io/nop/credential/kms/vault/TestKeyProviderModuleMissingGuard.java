/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.kms.vault;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W10 Phase 1 缺失守卫证明：{@code key-provider=vault} 而 KMS 模块未部署时
 * <b>启动失败</b>（default-bean 守卫触发，设计 §4.3 假安全场景被结构性堵死）。
 *
 * <p>模拟方式：经 {@code nop.ioc.app-beans-file.skip-pattern} 跳过
 * {@code app-kms-vault.beans.xml} 的自动装载——容器合并结果等价于"部署中不存在
 * KMS 模块"（其余平台与 credential 模块 bean 全部在场）。此时
 * {@code nopCredentialKeyProvider} 由 default bean 兜底注册为
 * {@code DefaultCredentialKeyProvider}，其 {@code @PostConstruct} 守卫拦截
 * 非 local 配置 → 应用初始化失败（fail-closed）。
 *
 * <p>NopIoC 的 {@code ioc:default} 语义是<b>无条件兜底</b>（bean id 改写为
 * {@code $DEFAULT$<id>} 并附加 missing-bean 条件，与配置项取值无关）——模块缺失时
 * default bean 照常注册并回退 local。若没有守卫，"配置了 KMS 实际跑 local"的
 * 假安全将静默发生；守卫使该场景显式启动失败。
 *
 * <p>工程注记：plain JUnit（initializeTo 分阶段推进），避免 NopAutoTest 全量容器
 * 在 KMS 模块在 classpath 且 key-provider=vault 时先行构造 Vault bean
 * （nopCrudBizInitializer 强制构造 BizModel 依赖链）造成与被测场景无关的失败。
 */
public class TestKeyProviderModuleMissingGuard {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        BaseTestCase.setTestConfig("nop.credential.key-provider", "vault");
        // 模拟 KMS 模块未部署：跳过其 beans 文件的自动装载
        BaseTestCase.setTestConfig("nop.ioc.app-beans-file.skip-pattern",
                "/nop/credential/beans/app-kms-vault.beans.xml");
    }

    @AfterAll
    public static void destroy() {
        BaseTestCase.clearTestConfig("nop.credential.key-provider");
        BaseTestCase.clearTestConfig("nop.ioc.app-beans-file.skip-pattern");
        CoreInitialization.destroy();
    }

    @Test
    public void containerInitFailsWhenVaultConfiguredButModuleMissing() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            // 推进到 IoC 容器构建/启动：BizModel 依赖链触发 default bean 构造；
            // 若容器惰性未触发，则显式按名构造兜底注册的 nopCredentialKeyProvider。
            CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_POST_PROCESS);
            BeanContainer.instance().getBean("nopCredentialKeyProvider");
        });
        assertTrue(findErrorCode(ex, "nop.err.credential.key-provider-module-missing"),
                "守卫错误码必须在异常链中, got: " + ex);
    }

    /**
     * 守卫错误可能被容器/初始化器包装，沿 cause 链查找目标错误码。
     */
    private static boolean findErrorCode(Throwable e, String errorCode) {
        while (e != null) {
            if (e instanceof NopException && errorCode.equals(((NopException) e).getErrorCode())) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
