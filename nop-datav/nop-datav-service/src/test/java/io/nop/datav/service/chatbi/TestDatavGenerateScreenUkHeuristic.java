package io.nop.datav.service.chatbi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-3 focused test: {@link DatavGenerateScreenExecutor#isUniqueConstraintViolation(Exception)} 缩窄验证。
 *
 * <p><b>Chosen approach</b>（plan 文档化的 alternative）：mocking {@code IOrmTemplate}（~40 方法的接口）
 * 从公共 {@code executeAsync} 入口注入一个非 UK 约束异常不可行/不实际，故将
 * {@code isUniqueConstraintViolation} 由 {@code private} 改为包可见（test-visible），直接对启发式
 * 函数做确定性测试。本测试与 {@code io.nop.datav.service.entity.TestDatavGenerateScreenExecutor} 互补：
 * 后者经真实 DB 验证 UK 冲突（{@code testDuplicateScreenNameRejected}）走预查路径，本测试验证 DB 抛
 * 异常回退路径的启发式判定不再误报非 UK 约束。</p>
 *
 * <p>覆盖三方言 UK 模式（H2/MySQL/Postgres）仍正确命中，而 CHECK/FK/NOT NULL/裸 "constraint"/"uk_"
 * 不再被误判为 UK。</p>
 */
public class TestDatavGenerateScreenUkHeuristic {

    // ==================== AR-3 narrowing：非 UK 约束不再误报为重名 ====================

    @Test
    public void testCheckConstraintViolationNotMisclassifiedAsUnique() {
        // 修复前：含 "constraint" → 误报 true；修复后 → false
        assertFalse(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("Check constraint violation: AMOUNT must be > 0")),
                "CHECK constraint violation must NOT be classified as unique-constraint (AR-3 narrowing)");
    }

    @Test
    public void testForeignKeyConstraintViolationNotMisclassifiedAsUnique() {
        assertFalse(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("Referential integrity constraint violation: FK_SCREEN_WIDGET")),
                "FK constraint violation must NOT be classified as unique-constraint");
    }

    @Test
    public void testNotNullConstraintViolationNotMisclassifiedAsUnique() {
        assertFalse(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("NULL not allowed for column SCREEN_NAME")),
                "NOT NULL violation (no unique/duplicate substring) must NOT be classified as unique-constraint");
    }

    @Test
    public void testBareConstraintWordNotMisclassifiedAsUnique() {
        // 修复前："constraint" 命中 → true；修复后 → false
        assertFalse(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("Integrity constraint violation")),
                "bare 'constraint' without unique/duplicate must NOT be classified as unique-constraint (AR-3)");
    }

    @Test
    public void testBareUkUnderscoreNotMisclassifiedAsUnique() {
        // 修复前："uk_" 命中 → true；修复后 → false
        assertFalse(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("violated uk_screen_name index")),
                "bare 'uk_' without unique/duplicate must NOT be classified as unique-constraint (AR-3)");
    }

    // ==================== 三方言真实 UK 模式仍正确命中 ====================

    @Test
    public void testH2UniqueViolationDetected() {
        assertTrue(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("Unique index or primary key violation: \"UK_SCREEN_NAME...\"")),
                "H2 UK message contains 'unique' → must be detected as unique-constraint");
    }

    @Test
    public void testMysqlDuplicateEntryDetected() {
        assertTrue(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("Duplicate entry 'foo' for key 'uk_screen_name'")),
                "MySQL UK message contains 'duplicate' → must be detected as unique-constraint");
    }

    @Test
    public void testPostgresDuplicateKeyDetected() {
        assertTrue(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_screen_name\"")),
                "Postgres UK message contains both 'duplicate' and 'unique' → must be detected as unique-constraint");
    }

    // ==================== 边界：null message ====================

    @Test
    public void testNullMessageReturnsFalse() {
        assertFalse(DatavGenerateScreenExecutor.isUniqueConstraintViolation(
                        new RuntimeException()),
                "exception with null message must return false (no heuristic match)");
    }
}
