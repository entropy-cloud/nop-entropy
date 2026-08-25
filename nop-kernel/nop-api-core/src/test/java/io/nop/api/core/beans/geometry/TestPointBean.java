/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestPointBean {

    /**
     * 回归："[lng,lat]"解析的y分量起点必须跳过逗号，否则合法输入永远解析失败。
     */
    @Test
    public void testFromLngLatString() {
        PointBean point = PointBean.fromLngLatString("[113.5,22.3]");
        assertEquals(113.5, point.getLng());
        assertEquals(22.3, point.getLat());

        assertNull(PointBean.fromLngLatString(null));
        assertNull(PointBean.fromLngLatString(""));
    }

    /**
     * 回归："[lat,lng]"解析的x分量起点必须跳过逗号。
     */
    @Test
    public void testFromLatLngString() {
        PointBean point = PointBean.fromLatLngString("[22.3,113.5]");
        assertEquals(113.5, point.getLng());
        assertEquals(22.3, point.getLat());
    }

    @Test
    public void testFromWktString() {
        PointBean point = PointBean.fromWktString("POINT(113.5 22.3)");
        assertEquals(113.5, point.getLng());
        assertEquals(22.3, point.getLat());
    }

    @Test
    public void testRoundTrip() {
        PointBean point = new PointBean(113.5, 22.3);
        assertEquals(point, PointBean.fromLngLatString(point.toLngLatString()));
        assertEquals(point, PointBean.fromLatLngString(point.toLatLngString()));
        assertEquals(point, PointBean.fromWktString(point.toWktString()));
    }
}
