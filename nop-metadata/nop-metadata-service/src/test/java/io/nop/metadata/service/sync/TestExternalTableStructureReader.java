/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link ExternalTableStructureReader} 的方言门禁：首版仅 MySQL/PostgreSQL/H2 支持，
 * 其余方言（ClickHouse/Oracle 等）在入口显式抛 {@link UnsupportedOperationException}（非静默跳过）。
 *
 * <p>同包测试，直访包级门禁方法，无需真实非 H2 数据库即可覆盖"不支持方言显式失败"路径
 * （见 plan P2-2 item 1.5 / Exit Criteria「不支持的方言显式失败，不静默跳过」）。
 */
public class TestExternalTableStructureReader {

    @Test
    public void testSupportedDialectsRecognized() {
        assertTrue(ExternalTableStructureReader.isSupportedDialect("MySQL"));
        assertTrue(ExternalTableStructureReader.isSupportedDialect("PostgreSQL"));
        assertTrue(ExternalTableStructureReader.isSupportedDialect("H2"));
    }

    @Test
    public void testUnsupportedDialectsRejected() {
        assertFalse(ExternalTableStructureReader.isSupportedDialect("Oracle"));
        assertFalse(ExternalTableStructureReader.isSupportedDialect("ClickHouse"));
        assertFalse(ExternalTableStructureReader.isSupportedDialect("Microsoft SQL Server"));
    }

    @Test
    public void testUnsupportedDialectThrowsExplicitly() {
        // 不支持方言必须显式抛 UnsupportedOperationException，不静默跳过
        assertThrows(UnsupportedOperationException.class,
                () -> ExternalTableStructureReader.requireSupportedProductName("Oracle"),
                "unsupported dialect must throw UnsupportedOperationException");
        assertThrows(UnsupportedOperationException.class,
                () -> ExternalTableStructureReader.requireSupportedProductName(null),
                "null dialect must throw UnsupportedOperationException");
    }

    @Test
    public void testSupportedDialectDoesNotThrow() {
        // 已支持方言不应抛异常
        ExternalTableStructureReader.requireSupportedProductName("MySQL");
        ExternalTableStructureReader.requireSupportedProductName("PostgreSQL");
        ExternalTableStructureReader.requireSupportedProductName("H2");
    }
}
