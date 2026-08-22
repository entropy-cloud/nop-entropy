/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.dialect.sqlserver;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.geo.type.GeometryTypeHandler;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.codec.db.sqlserver.Decoders;
import org.geolatte.geom.codec.db.sqlserver.Encoders;

import java.sql.Blob;

import static io.nop.orm.geo.OrmGeoErrors.ARG_CLASS_NAME;
import static io.nop.orm.geo.OrmGeoErrors.ERR_ORM_GEO_INVALID_GEOMETRY_OBJECT;

public class SqlServerGeometryTypeHandler extends GeometryTypeHandler {
    @Override
    protected Wkt.Dialect getWktDialect() {
        return Wkt.Dialect.SFA_1_2_1;
    }

    @Override
    protected String getGeomFromTextFunc() {
        return "geometry::STGeomFromText";
    }

    @Override
    protected Geometry parseDbValue(Object obj) {
        byte[] raw;
        if (obj == null) {
            return null;
        }
        if ((obj instanceof byte[])) {
            raw = (byte[]) obj;
        } else if (obj instanceof Blob) {
            raw = toByteArray((Blob) obj);
        } else {
            throw new NopException(ERR_ORM_GEO_INVALID_GEOMETRY_OBJECT).param(ARG_CLASS_NAME, obj.getClass().getName());
        }
        return Decoders.decode(raw);
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setNull(index);
        } else {
            Geometry geometry = toGeometry(value);
            byte[] bytes = Encoders.encode(geometry);
            params.setObject(index, bytes);
        }
    }
}
