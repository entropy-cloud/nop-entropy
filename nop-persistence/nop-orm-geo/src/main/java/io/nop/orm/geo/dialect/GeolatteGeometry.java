package io.nop.orm.geo.dialect;

import io.nop.api.core.json.IJsonString;
import io.nop.commons.type.GeometryObject;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;

public class GeolatteGeometry implements GeometryObject, IJsonString {
    private final Geometry<?> geometry;

    public GeolatteGeometry(Geometry<?> geometry) {
        this.geometry = geometry;
    }

    public Geometry<?> getGeometry() {
        return geometry;
    }

    public String toString() {
        return Wkt.toWkt(geometry, Wkt.Dialect.POSTGIS_EWKT_1);
    }
}
