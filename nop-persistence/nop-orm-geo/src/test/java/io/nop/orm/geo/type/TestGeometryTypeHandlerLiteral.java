/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.type;

import org.geolatte.geom.C2D;
import org.geolatte.geom.Point;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 GEO-01：GeometryTypeHandler.toLiteral 必须以逗号分隔 WKT 与 SRID，
 * 生成 <code>ST_GeomFromText('WKT',srid)</code> 而非非法的 <code>ST_GeomFromText('WKT'.srid)</code>。
 */
public class TestGeometryTypeHandlerLiteral {

    @Test
    public void testToLiteralUsesCommaBeforeSrid() {
        GeometryTypeHandler handler = new GeometryTypeHandler();
        Point<C2D> pt = new Point<>(new C2D(1, 2), CoordinateReferenceSystems.PROJECTED_2D_METER);

        String literal = handler.toLiteral(pt, null);

        // 修复前生成 ST_GeomFromText('POINT (1 2)'.0)，SQL 语法非法
        assertTrue(literal.matches("ST_GeomFromText\\('POINT ?\\(1 2\\)', ?\\d+\\)"),
                "几何字面量应为 ST_GeomFromText('WKT',srid) 形式，实际: " + literal);
    }
}
