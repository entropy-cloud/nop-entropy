/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.geo.dialect.oracle;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.geo.type.GeometryTypeHandler;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.codec.db.oracle.Decoders;
import org.geolatte.geom.codec.db.oracle.DefaultConnectionFinder;
import org.geolatte.geom.codec.db.oracle.Encoders;
import org.geolatte.geom.codec.db.oracle.OracleJDBCTypeFactory;
import org.geolatte.geom.codec.db.oracle.SDOGeometry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Struct;

public class OracleGeometryTypeHandler extends GeometryTypeHandler {
    private OracleJDBCTypeFactory typeFactory;

    public OracleGeometryTypeHandler() {
        this.typeFactory = new OracleJDBCTypeFactory(new DefaultConnectionFinder());
    }

    @Override
    public String toLiteral(Object value, IDialect dialect) {
        Geometry<?> geom = toGeometry(value);
        StringBuilder sb = new StringBuilder();
        sb.append("ST_GEOMETRY.FROM_WKT");
        sb.append("('");
        sb.append(Wkt.toWkt(geom, Wkt.Dialect.SFA_1_2_1));
        sb.append("',");
        sb.append((Math.max(geom.getSRID(), 0)));
        sb.append(").Geom");
        return sb.toString();
    }

    @Override
    protected Geometry parseDbValue(Object object) {
        if (object == null)
            return null;
        final SDOGeometry sdoGeom = SDOGeometry.load((Struct) object);
        return Decoders.decode(sdoGeom);
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setNull(index);
        } else {
            final Geometry geometry = toGeometry(value);
            final Object dbGeom = toNative(geometry, (Connection) params.getNativeConnection());
            params.setObject(index, dbGeom);
        }
    }

    Object toNative(Geometry geom, Connection conn) {
        try {
            final SDOGeometry sdoGeom = Encoders.encode(geom);
            return typeFactory.createStruct(sdoGeom, conn);
        } catch (SQLException e) {
            throw NopException.adapt(e);
        }
    }
}
