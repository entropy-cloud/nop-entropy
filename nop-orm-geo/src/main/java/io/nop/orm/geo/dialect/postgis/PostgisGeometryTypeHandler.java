/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.dialect.postgis;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.geo.type.GeometryTypeHandler;
import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.WkbDecoder;
import org.geolatte.geom.codec.WkbEncoder;
import org.geolatte.geom.codec.Wkt;
import org.postgresql.util.PGobject;

public class PostgisGeometryTypeHandler extends GeometryTypeHandler {
    @Override
    protected boolean isLiteralIncludeSRID() {
        return false;
    }

    @Override
    protected Wkt.Dialect getWktDialect() {
        return Wkt.Dialect.POSTGIS_EWKT_1;
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        final PGobject geom = toPGobject(value);
        params.setObject(index, geom);
    }

    private PGobject toPGobject(Object value) {
        try {
            final WkbEncoder encoder = Wkb.newEncoder(getWkbDialect());
            final Geometry<?> geometry = toGeometry(value);
            final String hexString = encoder.encode(geometry, ByteOrder.NDR).toString();
            final PGobject obj = new PGobject();
            obj.setType("geometry");
            obj.setValue(hexString);
            return obj;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }


    @Override
    public Geometry<?> parseDbValue(Object object) {
        if (object == null) {
            return null;
        }
        ByteBuffer buffer;
        if (object instanceof PGobject) {
            String pgValue = ((PGobject) object).getValue();
            if (pgValue == null) {
                return null;
            }
            if (pgValue.startsWith("00") || pgValue.startsWith("01")) {
                buffer = ByteBuffer.from(pgValue);
                final WkbDecoder decoder = Wkb.newDecoder(getWkbDialect());
                return decoder.decode(buffer);
            } else {
                return parseWkt(pgValue);
            }

        }
        throw new IllegalStateException("nop.err.orm.invalid-object:" + object.getClass().getCanonicalName());
    }
}
