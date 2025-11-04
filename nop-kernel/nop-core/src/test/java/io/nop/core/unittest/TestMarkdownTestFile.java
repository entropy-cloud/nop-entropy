/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.unittest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMarkdownTestFile extends BaseTestCase {
    @Test
    public void testParse() {
        MarkdownTestFile file = markdownTestFile(attachmentResource("test-block.md"));
        assertEquals(2, file.getSections().size());
        assertEquals("5. 闭包变量，变量引用", file.getSections().get(0).getTitle());
        assertEquals("6. 通过闭包访问可变变量", file.getSections().get(1).getTitle());
    }

    @Test
    public void testParse2() {
        MarkdownTestFile file = markdownTestFile(attachmentResource("test-block2.md"));
        assertEquals(1, file.getSections().size());
        assertEquals("1. 判断语句", file.getSections().get(0).getTitle());
    }
}
