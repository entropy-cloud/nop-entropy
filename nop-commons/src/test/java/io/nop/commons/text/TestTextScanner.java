/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import io.nop.commons.text.tokenizer.TextScanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTextScanner {
    @Test
    public void testNumber() {
        String str = "0 , 0.1 , 0.11 , 1.1E3,.0,0 ";
        TextScanner sc = TextScanner.fromString(null, str);
        Number num = sc.nextNumber();
        assertEquals(0, num);

        sc.skipBlank();
        sc.match(',');
        assertEquals(0.1, sc.nextNumber());
        sc.skipBlank();
        sc.match(',');

        assertEquals(0.11, sc.nextNumber());
        sc.skipBlank();
        sc.match(',');
        assertEquals(1.1E3, sc.nextNumber());
        sc.match(',');

        assertEquals(0.0, sc.nextNumber());
        sc.match(',');
        assertEquals(0, sc.nextNumber());
    }

    @Test
    public void testPeek() {
        String str = "abcdefgh123456";
        TextScanner sc = TextScanner.fromString(null, str);
        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            assertTrue(sc.startsWith(str.substring(i)));
            assertEquals(c, sc.cur);
            sc.next();
        }
    }

    @Test
    public void testLine() {
        String str = "\r\n\n\r\r\n";
        TextScanner sc = TextScanner.fromString(null, str);
        assertEquals('\r', sc.cur);
        assertEquals(1, sc.line);
        assertEquals(1, sc.col);
        sc.next();
        assertEquals(1, sc.line);
        assertEquals(2, sc.col);
        assertEquals('\n', sc.cur);
        sc.next();
        assertEquals(2, sc.line);
        assertEquals(1, sc.col);
        assertEquals('\n', sc.cur);
        sc.next();
        assertEquals(3, sc.line);
        assertEquals(1, sc.col);
        assertEquals('\r', sc.cur);
        sc.next();
        assertEquals(4, sc.line);
        assertEquals(1, sc.col);
        assertEquals('\r', sc.cur);
        sc.next();
        assertEquals(4, sc.line);
        assertEquals(2, sc.col);
        assertEquals('\n', sc.cur);
        sc.next();
    }

    @Test
    public void testCrlf() {
        String text = "a\r\n\r\n\r123";
        TextScanner sc = TextScanner.fromString(null, text);
        String line = sc.nextLine().toString();
        assertEquals("a", line);
        assertEquals('\r', sc.cur);
        assertEquals(1, sc.col);
        assertEquals(2, sc.line);

        line = sc.nextLine().toString();
        assertEquals("", line);

        line = sc.nextLine().toString();
        assertEquals("", line);

        line = sc.nextLine().toString();
        assertEquals("123", line);
    }

    @Test
    public void testConfigVar(){
        String text = "nop.test.a-b and x";
        TextScanner sc = TextScanner.fromString(null,text);
        assertEquals("nop.test.a-b",sc.nextConfigVar());
    }
}
