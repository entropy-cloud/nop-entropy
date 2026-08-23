/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.type;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.geo.dialect.db2.Db2GeometryTypeHandler;
import io.nop.orm.geo.dialect.postgis.PostgisGeometryTypeHandler;
import io.nop.orm.geo.dialect.sqlserver.SqlServerGeometryTypeHandler;
import org.geolatte.geom.Geometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestGeometryTypeHandlerErrors {

    static class ExposedBaseHandler extends GeometryTypeHandler {
        Geometry<?> parse(Object value) {
            return parseDbValue(value);
        }
    }

    static class ExposedPostgisHandler extends PostgisGeometryTypeHandler {
        Geometry<?> parse(Object value) {
            return parseDbValue(value);
        }
    }

    static class ExposedDb2Handler extends Db2GeometryTypeHandler {
        Geometry<?> parse(Object value) {
            return parseDbValue(value);
        }
    }

    static class ExposedSqlServerHandler extends SqlServerGeometryTypeHandler {
        Geometry<?> parse(Object value) {
            return parseDbValue(value);
        }
    }

    private static void assertInvalidGeometry(NopException e, Class<?> valueClass) {
        assertEquals("nop.err.orm.geo.invalid-geometry-object", e.getErrorCode());
        assertEquals(valueClass.getName(), e.getParam("className"));
    }

    @Test
    public void testBaseHandlerRejectsUnknownTypeWithErrorCode() {
        // 修复前抛无消息的 IllegalArgumentException
        assertInvalidGeometry(assertThrows(NopException.class, () -> new ExposedBaseHandler().parse("not-wkb")),
                String.class);
    }

    @Test
    public void testPostgisRejectsUnknownTypeWithErrorCode() {
        // 修复前抛 IllegalStateException 拼接伪错误码字符串
        assertInvalidGeometry(assertThrows(NopException.class, () -> new ExposedPostgisHandler().parse("not-wkb")),
                String.class);
    }

    @Test
    public void testDb2RejectsUnknownTypeWithErrorCode() {
        assertInvalidGeometry(assertThrows(NopException.class, () -> new ExposedDb2Handler().parse("not-wkb")),
                String.class);
    }

    @Test
    public void testSqlServerRejectsUnknownTypeWithErrorCode() {
        assertInvalidGeometry(assertThrows(NopException.class, () -> new ExposedSqlServerHandler().parse(3.14)),
                Double.class);
    }
}
