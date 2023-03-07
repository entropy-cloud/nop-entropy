/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.marker;

import io.nop.commons.text.marker.Markers.ValueMarker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMarkedString {
    @Test
    public void testTransform() {
        MarkedStringBuilder sb = new MarkedStringBuilder();
        sb.append("01");
        sb.appendWithValueMarker("XX", "a", "A", false);
        sb.append('4');
        sb.append("5UV");
        sb.appendMarker(new ValueMarker(6, 8, "b", "B", false));

        MarkedString str = sb.end();
        assertEquals("01XX45UV", str.getText());
        assertEquals(2, str.getMarkers().get(0).getBegin());
        assertEquals(4, str.getMarkers().get(0).getEnd());

        System.out.println(str.getDumpText());

        MarkedStringBuilder sb2 = new MarkedStringBuilder();
        sb2.appendWithTransform(sb, marker -> {
            if (marker.getBegin() == 2)
                return null;

            return new MarkedStringBuilder().append("abc");
        });

        assertEquals("01XX45abc", sb2.getText());
        assertEquals(1, sb2.getMarkers().size());
        assertEquals(2, sb2.getMarkers().get(0).getBegin());
    }

    @Test
    public void testTransformFirst() {
        MarkedStringBuilder sb = new MarkedStringBuilder();
        sb.append("01");
        sb.appendWithValueMarker("XX", "a", "A", false);
        sb.append('4');
        sb.append("5UVW");
        sb.appendMarker(new ValueMarker(6, 8, "b", "B", false));

        MarkedString str = sb.end();
        assertEquals("01XX45UVW", str.getText());
        assertEquals(2, str.getMarkers().get(0).getBegin());
        assertEquals(4, str.getMarkers().get(0).getEnd());

        System.out.println(str.getDumpText());

        MarkedStringBuilder sb2 = new MarkedStringBuilder();
        sb2.appendWithTransform(sb, marker -> {
            if (marker.getBegin() == 6)
                return null;

            return new MarkedStringBuilder().append("abc");
        });

        assertEquals("01abc45UVW", sb2.getText());
        assertEquals(1, sb2.getMarkers().size());
        assertEquals(7, sb2.getMarkers().get(0).getBegin());

    }
}