package io.nop.sys.dao.naming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * check 审计 [P2]：SysDaoNamingService.cleanup 周期任务一次DB异常即被JDK调度器永久取消且无日志。
 * 修复后cleanup内部兜底try-catch，异常只记error日志不向上抛。
 */
public class TestNamingCleanupProtection {
    @Test
    public void testCleanupSwallowsExceptions() {
        SysDaoNamingService service = new SysDaoNamingService();
        // daoProvider未装配 → cleanup内部NPE，但不得向JDK调度器抛出（否则周期任务永久取消）
        assertDoesNotThrow(service::cleanup);
    }
}
