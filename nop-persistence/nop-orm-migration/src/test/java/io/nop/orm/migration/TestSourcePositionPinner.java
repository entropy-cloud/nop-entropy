/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_P0_PIN_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P0 钉定（设计 §4.2）：H2 下模拟位点对象（PositionProvider seam）+ 重复钉定取新值 +
 * 不支持方言显式失败。真实 mysql/pg 查询路径归 docker-gated 测试面。
 */
public class TestSourcePositionPinner {

    @Test
    public void testPinnedPositionReturned() {
        SourcePositionPinner pinner = new SourcePositionPinner();
        pinner.setPositionProvider((jdbc, querySpace, connectorType) ->
                SourcePosition.mysql("binlog.000005", 900L, null));
        SourcePosition p0 = pinner.pin(null, "", "mysql");
        assertEquals("binlog.000005", p0.getBinlogFile());
        assertEquals(900L, p0.getBinlogPos());
    }

    @Test
    public void testRepinnedTakesNewValue() {
        // 设计 §4.1 PREPARE 语义：P0 钉定重复执行取新值
        SourcePositionPinner pinner = new SourcePositionPinner();
        pinner.setPositionProvider((jdbc, querySpace, connectorType) ->
                SourcePosition.postgres("0/1"));
        assertEquals("0/1", pinner.pin(null, "", "postgres").getLsn());
        pinner.setPositionProvider((jdbc, querySpace, connectorType) ->
                SourcePosition.postgres("0/2"));
        assertEquals("0/2", pinner.pin(null, "", "postgres").getLsn());
    }

    @Test
    public void testNullProviderResultFailsTyped() {
        SourcePositionPinner pinner = new SourcePositionPinner();
        pinner.setPositionProvider((jdbc, querySpace, connectorType) -> null);
        NopException e = assertThrows(NopException.class, () -> pinner.pin(null, "", "mysql"));
        assertEquals(ERR_MIG_P0_PIN_FAILED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testUnknownConnectorTypeFailsTyped() {
        // 白名单外数据库无 CDC 位点查询路径（v1 只支持停写全量形态，设计 §三.4）
        SourcePositionPinner pinner = new SourcePositionPinner();
        pinner.setPositionProvider(new SourcePositionPinner.JdbcPositionProvider());
        NopException e = assertThrows(NopException.class, () -> pinner.pin(null, "", "oracle"));
        assertEquals(ERR_MIG_P0_PIN_FAILED.getErrorCode(), e.getErrorCode());
    }
}
