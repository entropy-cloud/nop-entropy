package io.nop.sys.dao.coderule;

import io.nop.api.core.exceptions.NopException;
import io.nop.sys.dao.NopSysErrors;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * check2 审计 [P1]：编码规则 {@code @seq:N} 在序号超过 N 位时静默截断低位，
 * 序列全局单调递增不重置，截断后必然与历史编码重复。修复后超限抛
 * ERR_SYS_SEQ_VALUE_EXCEED_LIMIT 而非静默回绕。
 */
public class TestDefaultCodeRule {

    @Test
    public void testSeqWithinWidthIsLeftPadded() {
        DefaultCodeRule rule = new DefaultCodeRule();
        String code = rule.generate("D{@seq:4}", LocalDateTime.now(), () -> 42L, null);
        assertEquals("D0042", code);
    }

    @Test
    public void testSeqExceedingWidthThrowsInsteadOfTruncating() {
        DefaultCodeRule rule = new DefaultCodeRule();
        NopException ex = assertThrows(NopException.class,
                () -> rule.generate("D{@seq:2}", LocalDateTime.now(), () -> 123L, null));
        assertEquals(NopSysErrors.ERR_SYS_SEQ_VALUE_EXCEED_LIMIT.getErrorCode(), ex.getErrorCode());
        assertEquals("D{@seq:2}", ex.getParam(NopSysErrors.ARG_PATTERN));
    }
}
