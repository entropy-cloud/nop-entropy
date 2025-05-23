/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.util;

import io.nop.api.core.beans.geometry.PointBean;
import org.geolatte.geom.C2D;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.LineString;
import org.geolatte.geom.Point;
import org.geolatte.geom.Polygon;
import org.geolatte.geom.Position;
import org.geolatte.geom.PositionSequence;
import org.geolatte.geom.PositionSequenceBuilder;
import org.geolatte.geom.PositionSequenceBuilders;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.crs.CoordinateReferenceSystems;

import java.util.Collection;

public class GeoLatteHelper {
    public static Point<C2D> makePoint(double x, double y) {
        Point<C2D> pt = new Point<>(new C2D(x, y), CoordinateReferenceSystems.PROJECTED_2D_METER);
        return pt;
    }

    public static Point<C2D> makePoint(PointBean pos) {
        return makePoint(pos.getX(), pos.getY());
    }

    public static <P extends Position> PointBean toPointBean(Point<P> pt) {
        PointBean pos = new PointBean(pt.getPosition().getCoordinate(0), pt.getPosition().getCoordinate(1));
        return pos;
    }

    public static PositionSequence<C2D> toPositionSequence(Collection<PointBean> points) {
        PositionSequenceBuilder<C2D> builder = PositionSequenceBuilders.fixedSized(points.size(), C2D.class);
        for (PointBean pos : points) {
            builder.add(new C2D(pos.getX(), pos.getY()));
        }
        PositionSequence<C2D> seq = builder.toPositionSequence();
        return seq;
    }

    public static LineString<C2D> makeLine(Collection<PointBean> points) {
        PositionSequence<C2D> seq = toPositionSequence(points);
        LineString<C2D> line = new LineString<>(seq, CoordinateReferenceSystems.PROJECTED_2D_METER);
        return line;
    }

    public static Polygon<C2D> makePolygon(Collection<PointBean> points) {
        PositionSequence<C2D> seq = toPositionSequence(points);
        Polygon<C2D> polygon = new Polygon<>(seq, CoordinateReferenceSystems.PROJECTED_2D_METER);
        return polygon;
    }

    public static Geometry<?> decodeWktString(String str) {
        if (str == null || str.isEmpty())
            return null;
        return Wkt.newDecoder().decode(str);
    }
}