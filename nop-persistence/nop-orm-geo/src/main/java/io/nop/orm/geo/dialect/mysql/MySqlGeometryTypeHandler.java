/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.dialect.mysql;

import io.nop.orm.geo.type.GeometryTypeHandler;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.Wkt;

public class MySqlGeometryTypeHandler extends GeometryTypeHandler {
    @Override
    protected Wkt.Dialect getWktDialect() {
        return Wkt.Dialect.MYSQL_WKT;
    }

    @Override
    protected Wkb.Dialect getWkbDialect() {
        return Wkb.Dialect.MYSQL_WKB;
    }
}
