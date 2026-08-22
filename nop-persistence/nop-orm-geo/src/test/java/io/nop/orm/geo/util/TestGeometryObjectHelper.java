/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.util;

import io.nop.commons.type.GeometryObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestGeometryObjectHelper {

    @Test
    public void testEmptyWktStringReturnsNull() {
        // 修复前空串会生成包裹 null 的 GeolatteGeometry，空值判断失效且延后 NPE
        assertNull(GeometryObjectHelper.toGeometryObject("", null));
    }

    @Test
    public void testNullReturnsNull() {
        assertNull(GeometryObjectHelper.toGeometryObject(null, null));
    }

    @Test
    public void testValidWktReturnsGeometry() {
        GeometryObject geo = GeometryObjectHelper.toGeometryObject("POINT (1 2)", null);
        assertNotNull(geo);
    }
}
