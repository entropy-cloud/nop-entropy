/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.dialect.db2;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.geo.type.GeometryTypeHandler;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.db.db2.Db2ClobDecoder;
import org.geolatte.geom.codec.db.db2.Db2ClobEncoder;

import java.sql.Clob;

import static io.nop.orm.geo.OrmGeoErrors.ARG_CLASS_NAME;
import static io.nop.orm.geo.OrmGeoErrors.ERR_ORM_GEO_INVALID_GEOMETRY_OBJECT;

public class Db2GeometryTypeHandler extends GeometryTypeHandler {
    private Integer srid;

    public Integer getSrid() {
        return srid;
    }

    public void setSrid(Integer srid) {
        this.srid = srid;
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setNull(index);
            return;
        }
        final Geometry<?> geometry = toGeometry(value);
        final Db2ClobEncoder encoder = new Db2ClobEncoder();
        String encoded = encoder.encode(geometry);
        params.setObject(index, encoded);
    }

    @Override
    protected Geometry parseDbValue(Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof Clob) {
            Db2ClobDecoder decoder = new Db2ClobDecoder(srid);
            return decoder.decode((Clob) object);
        }

        throw new NopException(ERR_ORM_GEO_INVALID_GEOMETRY_OBJECT).param(ARG_CLASS_NAME, object.getClass().getName());
    }
}
