/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.geometry;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;

import java.io.Serializable;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.api.core.ApiErrors.ERR_INVALID_GEO_POINT_STRING;
import static io.nop.api.core.ApiErrors.ERR_INVALID_GEO_POINT_WKT_STRING;

@DataBean
public class PointBean implements Serializable {
    private static final long serialVersionUID = -1375536295354797812L;
    private final double x;
    private final double y;

    public PointBean(@JsonProperty("x") double x,
                     @JsonProperty("y") double y) {
        this.x = x;
        this.y = y;
    }

    public String toString() {
        return toLngLatString();
    }

    @PropMeta(propId = 1)
    public double getX() {
        return x;
    }

    @PropMeta(propId = 2)
    public double getY() {
        return y;
    }

    @PropMeta(propId = 3)
    public double getLng() {
        return x;
    }

    @PropMeta(propId = 4)
    public double getLat() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PointBean))
            return false;

        PointBean that = (PointBean) o;

        if (Double.compare(that.x, x) != 0)
            return false;
        return Double.compare(that.y, y) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(x);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }


    /**
     * leaflet库使用LatLng字符串
     *
     * @return
     */
    public String toLatLngString() {
        return "[" + getLat() + "," + getLng() + "]";
    }

    public String toLngLatString() {
        return "[" + getLng() + "," + getLat() + "]";
    }

    /**
     * postgis WKT编码格式
     *
     * @return
     */
    public String toWktString() {
        return "POINT(" + getLng() + " " + getLat() + ")";
    }

    @StaticFactoryMethod
    public static PointBean fromLngLatString(String str) {
        if (str == null || str.isEmpty())
            return null;
        if (!str.startsWith("[") || !str.endsWith("]"))
            throw new NopException(ERR_INVALID_GEO_POINT_WKT_STRING).param(ARG_VALUE, str);
        int pos = str.indexOf(',');
        if (pos < 0)
            throw new NopException(ERR_INVALID_GEO_POINT_WKT_STRING).param(ARG_VALUE, str);
        double x = ConvertHelper.toPrimitiveDouble(str.substring(1, pos), NopException::new);
        double y = ConvertHelper.toPrimitiveDouble(str.substring(pos, str.length() - 1),
                NopException::new);
        return new PointBean(x, y);
    }

    public static PointBean fromLatLngString(String str) {
        if (str == null || str.isEmpty())
            return null;
        if (!str.startsWith("[") || !str.endsWith("]"))
            throw new NopException(ERR_INVALID_GEO_POINT_STRING).param(ARG_VALUE, str);
        int pos = str.indexOf(',');
        if (pos < 0)
            throw new NopException(ERR_INVALID_GEO_POINT_WKT_STRING).param(ARG_VALUE, str);
        double y = ConvertHelper.toPrimitiveDouble(str.substring(1, pos), NopException::new);
        double x = ConvertHelper.toPrimitiveDouble(str.substring(pos, str.length() - 1),
                NopException::new);
        return new PointBean(x, y);
    }

    public static PointBean fromWktString(String str) {
        if (str == null || str.isEmpty())
            return null;
        if (!str.startsWith("POINT(") || !str.endsWith(")"))
            throw new NopException(ERR_INVALID_GEO_POINT_WKT_STRING).param(ARG_VALUE, str);

        int pos = str.indexOf(' ');
        if (pos < 0)
            throw new NopException(ERR_INVALID_GEO_POINT_WKT_STRING).param(ARG_VALUE, str);
        double x = ConvertHelper.toPrimitiveDouble(str.substring("POINT(".length(), pos), NopException::new);
        double y = ConvertHelper.toPrimitiveDouble(str.substring(pos, str.length() - 1), NopException::new);
        return new PointBean(x, y);
    }
}