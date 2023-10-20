/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.geo.dialect.db2;

import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.geo.type.GeometryTypeHandler;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.db.db2.Db2ClobDecoder;
import org.geolatte.geom.codec.db.db2.Db2ClobEncoder;

import java.sql.Clob;

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

        throw new IllegalStateException("nop.err.orm.invalid-geometry-value:" + object.getClass());
    }
}
