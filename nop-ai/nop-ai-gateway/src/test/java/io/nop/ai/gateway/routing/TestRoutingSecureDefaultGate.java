package io.nop.ai.gateway.routing;

import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.core.NopAiCoreException;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecureDefault 门禁（plan 2424/WI5）：DefaultSelectionStrategy 随选路引擎自
 * nop-ai-core 迁入本模块后，agent 的 TestInvariantGate1SecureDefault 因 reactor
 * 禁环无法扫描 gateway 类——本测试承接其等价覆盖：校验引擎 Default* 类携带
 * {@link SecureDefault} 声明注解，以及 CandidateHealthProvider 的
 * ERR_AI_CORE_INVALID_ARGUMENT 错误码契约（原 core TestNopAiCoreErrorsContract
 * 用例随类迁移）。
 */
public class TestRoutingSecureDefaultGate {

    @Test
    void defaultSelectionStrategyDeclaresSecureDefault() throws Exception {
        Class<?> clazz = DefaultSelectionStrategy.class;
        assertNotNull(clazz.getAnnotation(SecureDefault.class),
                "DefaultSelectionStrategy must declare @SecureDefault (I0 invariant, moved from agent gate by plan 2424)");
    }

    @Test
    void candidateHealthProviderInvalidArgumentContract() {
        CandidateHealthProvider provider = new CandidateHealthProvider(null, null);
        NopAiCoreException ex = assertThrows(NopAiCoreException.class,
                () -> provider.healthOf(null));
        assertEquals("nop.err.ai.core.invalid-argument", ex.getErrorCode(),
                "null candidate must fail loud with the core invalid-argument code");
        // NopException adaptation sanity (mirrors the migrated core contract case)
        assertNotNull(NopException.adapt(ex));
    }
}
