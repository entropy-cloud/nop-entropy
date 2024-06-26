package io.nop.orm.geo.util;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.GeometryObject;
import io.nop.orm.geo.dialect.GeolatteGeometry;
import org.geolatte.geom.Geometry;

import java.util.function.Function;

public class GeometryObjectHelper {
    public static void register() {
        SysConverterRegistry.instance().registerConverter("toGeometryObject", GeometryObject.class, GeometryObjectHelper::toGeometryObject);
    }

    public static GeometryObject toGeometryObject(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;
        if (value instanceof GeometryObject)
            return (GeometryObject) value;
        if (value instanceof String) {
            Geometry<?> geo = GeoLatteHelper.decodeWktString(value.toString());
            return new GeolatteGeometry(geo);
        }

        if (value instanceof Geometry)
            return new GeolatteGeometry((Geometry<?>) value);

        return ConvertHelper.handleError(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL, null, GeometryObject.class,
                value, errorFactory);
    }
}
