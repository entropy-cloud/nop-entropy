/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSourceLocation {
    @Test
    public void testSerialize() {
        SourceLocation loc = new SourceLocation("a", 1, 2, 3, 0, "s", "c", "t");
        String str = loc.toString();
        SourceLocation loc2 = SourceLocation.parse(str);
        assertEquals(loc, loc2);
        assertEquals("a", loc.getPath());
    }

    @Test
    public void testTag() {
        SourceLocation loc = new SourceLocation("path", 100, 2, 3, 0, null, null, "t");
        String str = loc.toString();
        SourceLocation loc2 = SourceLocation.parse(str);
        assertEquals(loc, loc2);
        assertEquals("path", loc.getPath());
        assertEquals(100, loc.getLine());
        assertEquals("t", loc.getRef());
        assertEquals(2, loc.getCol());
    }

    @Test
    public void testConstruct() {
        SourceLocation loc = SourceLocation.fromPath("/a/b/c.html");
        assertEquals(1, loc.getLine());
        assertEquals(0, loc.getCol());
        assertEquals(0, loc.getLen());
        assertEquals("/a/b/c.html", loc.getPath());
        assertEquals("[1:0:0:0]/a/b/c.html", loc.toString());
    }
}