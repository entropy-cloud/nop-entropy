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
 * W10 Phase 1 守卫的 master-keys 残留分支（模块缺失场景可达性证明）：
 * {@code key-provider=vault} + {@code master-keys} 非空 且 KMS 模块未部署 →
 * 容器初始化抛 {@code nop.err.credential.master-keys-residual}（密钥来源混合拒绝；
 * 该检查置于守卫正是为了模块缺失场景下也可达，W10 plan Phase 1）。
 *
 * <p>模拟方式与 {@link TestKeyProviderModuleMissingGuard} 相同
 * （skip-pattern 跳过 KMS beans 文件自动装载）。
 */
public class TestKeyProviderGuardMasterKeysResidual {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        BaseTestCase.setTestConfig("nop.credential.key-provider", "vault");
        BaseTestCase.setTestConfig("nop.credential.master-keys", "keyA:leftover-local-pass");
        BaseTestCase.setTestConfig("nop.ioc.app-beans-file.skip-pattern",
                "/nop/credential/beans/app-kms-vault.beans.xml");
    }

    @AfterAll
    public static void destroy() {
        BaseTestCase.clearTestConfig("nop.credential.key-provider");
        BaseTestCase.clearTestConfig("nop.credential.master-keys");
        BaseTestCase.clearTestConfig("nop.ioc.app-beans-file.skip-pattern");
        CoreInitialization.destroy();
    }

    @Test
    public void containerInitFailsOnMasterKeysResidualWhenModuleMissing() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_POST_PROCESS);
            BeanContainer.instance().getBean("nopCredentialKeyProvider");
        });
        assertTrue(findErrorCode(ex, "nop.err.credential.master-keys-residual"),
                "守卫 residual 错误码必须在异常链中, got: " + ex);
    }

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
