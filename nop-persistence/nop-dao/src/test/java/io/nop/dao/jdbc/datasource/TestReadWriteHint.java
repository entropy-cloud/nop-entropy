/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.PRIMARY;
import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.REPLICA;
import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.UNSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2319 Phase 1：ReadWriteHint ThreadLocal三态原语的作用域语义
 * （对齐TestDynamicDataSource的runWith恢复断言模式）
 */
public class TestReadWriteHint {

    @AfterEach
    public void tearDown() {
        // 清理静态ThreadLocal，避免影响其他测试（leave(UNSET)即移除ThreadLocal值）
        ReadWriteHint.leave(UNSET);
    }

    @Test
    public void testDefaultIsUnset() {
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }

    @Test
    public void testRunWithRestoresPreviousHint() {
        String used = ReadWriteHint.runWithPrimary(() -> {
            assertEquals(PRIMARY, ReadWriteHint.currentHint());
            return ReadWriteHint.runWithReplica(() -> {
                assertEquals(REPLICA, ReadWriteHint.currentHint());
                return "inner";
            });
        });
        assertEquals("inner", used);
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }

    @Test
    public void testRunWithKeepsPreexistingHint() {
        ReadWriteHint.runWithPrimary(() -> {
            ReadWriteHint.runWithReplica(() -> "ok");
            // 内层结束后恢复外层的值而不是清理为UNSET
            assertEquals(PRIMARY, ReadWriteHint.currentHint());
            return null;
        });
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }

    @Test
    public void testRunWithRestoresOnException() {
        assertThrows(IllegalStateException.class, () -> ReadWriteHint.runWithReplica(() -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(UNSET, ReadWriteHint.currentHint(), "hint must be cleaned even if task throws");
    }

    @Test
    public void testRunWithReadOnlyIsReplicaAlias() {
        ReadWriteHint.runWithReadOnly(() -> {
            assertEquals(REPLICA, ReadWriteHint.currentHint());
        });
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }

    @Test
    public void testRunnableOverloads() {
        ReadWriteHint.runWithPrimary(() -> assertEquals(PRIMARY, ReadWriteHint.currentHint()));
        ReadWriteHint.runWithReplica(() -> assertEquals(REPLICA, ReadWriteHint.currentHint()));
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }
}
