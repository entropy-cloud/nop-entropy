/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXDefCommentParser {
    @Test
    public void testParse() {
        String str = "abc \n  @x [XX] x123 \n  @b zzz\n  yyy";

        XDefComment comment = new XDefCommentParser().parseComment(null, str);
        assertEquals("abc", comment.getMainDescription());
        assertEquals("XX", comment.getSubDisplayName("x"));
        assertEquals("x123", comment.getSubDescription("x"));

        assertEquals("zzz\nyyy", comment.getSubDescription("b"));
    }
}
