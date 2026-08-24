/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.io.stream;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSafeLineReader {

    @Test
    public void testCountLines() {
        // 以换行符结尾的文件不应多计一行
        assertEquals(1, SafeLineReader.countLines(new StringReader("a\n")));
        assertEquals(2, SafeLineReader.countLines(new StringReader("a\nb\n")));
        // 不以换行符结尾的文件
        assertEquals(1, SafeLineReader.countLines(new StringReader("a")));
        assertEquals(2, SafeLineReader.countLines(new StringReader("a\nb")));
        // 空流
        assertEquals(0, SafeLineReader.countLines(new StringReader("")));
    }

    @Test
    public void testCountLinesWithMaxLines() {
        assertEquals(1, SafeLineReader.countLines(new StringReader("a\nb\n"), 1));
        assertEquals(2, SafeLineReader.countLines(new StringReader("a\nb\n"), 100));
        assertEquals(1, SafeLineReader.countLines(new StringReader("a"), 100));
    }
}
