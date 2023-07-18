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
