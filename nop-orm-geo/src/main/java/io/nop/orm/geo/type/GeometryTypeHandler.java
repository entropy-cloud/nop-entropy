/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.type;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.GeometryObject;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.IoHelper;
import io.nop.dao.dialect.IDataTypeHandler;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameters;
import io.nop.orm.geo.dialect.GeolatteGeometry;
import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.C2D;
import org.geolatte.geom.Envelope;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.Polygon;
import org.geolatte.geom.PositionSequence;
import org.geolatte.geom.PositionSequenceBuilders;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.WkbDecoder;
import org.geolatte.geom.codec.WkbEncoder;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.codec.WktDecoder;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.geolatte.geom.jts.JTS;

import java.io.InputStream;
import java.sql.Blob;

public class GeometryTypeHandler implements IDataTypeHandler {

    @Override
    public StdSqlType getStdSqlType() {
        return StdSqlType.GEOMETRY;
    }

    protected String getGeomFromTextFunc() {
        return "ST_GeomFromText";
    }

    protected Wkt.Dialect getWktDialect() {
        return Wkt.Dialect.SFA_1_1_0;
    }

    protected Wkb.Dialect getWkbDialect() {
        return Wkb.Dialect.POSTGIS_EWKB_1;
    }

    protected Geometry toGeometry(Object value) {
        if (value instanceof GeolatteGeometry) return ((GeolatteGeometry) value).getGeometry();
        return (Geometry) value;
    }

    protected ByteOrder getByteOrder() {
        return ByteOrder.NDR;
    }

    protected boolean isLiteralIncludeSRID() {
        return true;
    }

    @Override
    public String toLiteral(Object value, IDialect dialect) {
        Geometry geom = toGeometry(value);
        StringBuilder sb = new StringBuilder();
        sb.append(getGeomFromTextFunc());
        sb.append("('").append(Wkt.toWkt(geom, getWktDialect()));
        sb.append("'");
        if (isLiteralIncludeSRID()) {
            sb.append('.').append(Math.max(geom.getSRID(), 0));
        }
        sb.append(')');
        return sb.toString();
    }


    @Override
    public Object fromLiteral(String text, IDialect dialect) {
        return null;
    }

    @Override
    public boolean isJavaType(Object value) {
        return value instanceof Geometry || value instanceof GeolatteGeometry;
    }

    @Override
    public Object getValue(IDataParameters params, int index) {
        Object value = params.getObject(index);
        return toJavaValue(parseDbValue(value));
    }

    protected GeometryObject toJavaValue(Geometry geometry) {
        if (geometry == null) return null;
        return new GeolatteGeometry(geometry);
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        final Geometry geom = toGeometry(value);
        params.setBytes(index, toBytes(geom, getWkbDialect(), getByteOrder()));
    }

    protected byte[] toBytes(Geometry geom, Wkb.Dialect dialect, ByteOrder byteOrder) {
        final WkbEncoder encoder = Wkb.newEncoder(dialect);
        final ByteBuffer buffer = encoder.encode(geom, byteOrder);
        return (buffer == null ? null : buffer.toByteArray());
    }

    protected Geometry parseDbValue(Object object) {
        if (object == null) {
            return null;
        }
        try {
            if (object instanceof org.locationtech.jts.geom.Geometry) {
                return JTS.from((org.locationtech.jts.geom.Geometry) object);
            }
            final WkbDecoder decoder = Wkb.newDecoder(getWkbDialect());
            if (object instanceof Blob) {
                return decoder.decode(toByteBuffer((Blob) object));
            } else if (object instanceof byte[]) {
                return decoder.decode(ByteBuffer.from((byte[]) object));
            } else if (object instanceof org.locationtech.jts.geom.Envelope) {
                return toPolygon(JTS.from((org.locationtech.jts.geom.Envelope) object));
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private static Geometry<C2D> toPolygon(Envelope env) {
        final PositionSequence<C2D> ps = PositionSequenceBuilders.fixedSized(4, C2D.class).add(env.lowerLeft().getCoordinate(0), env.lowerLeft().getCoordinate(1)).add(env.lowerLeft().getCoordinate(0), env.upperRight().getCoordinate(1)).add(env.upperRight().getCoordinate(0), env.upperRight().getCoordinate(1)).add(env.lowerLeft().getCoordinate(0), env.lowerLeft().getCoordinate(1)).toPositionSequence();
        return new Polygon<C2D>(ps, CoordinateReferenceSystems.PROJECTED_2D_METER);
    }

    protected static ByteBuffer toByteBuffer(Blob blob) {
        return ByteBuffer.from(toByteArray(blob));
    }

    protected static byte[] toByteArray(Blob blob) {
        InputStream is = null;
        try {
            is = blob.getBinaryStream();
            return IoHelper.readBytes(is);
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(is);
        }
    }

    protected Geometry parseWkt(String text) {
        final WktDecoder decoder = Wkt.newDecoder(getWktDialect());
        return decoder.decode(text);
    }
}
