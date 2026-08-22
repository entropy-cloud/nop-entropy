/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.tokenizer.TextScanner;
import org.junit.jupiter.api.Test;

import static io.nop.commons.CommonErrors.ERR_SCAN_STRING_NOT_END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void testNextJsonStringFastPath() {
        // 纯 ASCII 无转义（快路径命中）
        TextScanner sc = TextScanner.fromString(null, "\"hello world\"");
        assertEquals("hello world", sc.nextJsonString());
        assertTrue(sc.isEnd());

        // 空字符串
        sc = TextScanner.fromString(null, "\"\"");
        assertEquals("", sc.nextJsonString());
        assertTrue(sc.isEnd());
    }

    @Test
    public void testNextJsonStringEscapeFallback() {
        // 含 unicode 转义（回落慢路径）
        TextScanner sc = TextScanner.fromString(null, "\"a\\u4e2d\\nb\"");
        assertEquals("a中\nb", sc.nextJsonString());
        assertTrue(sc.isEnd());

        // 转义斜杠与引号
        sc = TextScanner.fromString(null, "\"a\\/b\\\"c\"");
        assertEquals("a/b\"c", sc.nextJsonString());
        assertTrue(sc.isEnd());
    }

    @Test
    public void testNextJsonStringLocationBookkeeping() {
        // 快路径与慢路径的行列号簿记必须逐位一致
        // 快路径：同一行内两个连续字符串
        TextScanner sc = TextScanner.fromString(null, "\"ab\",\"cd\"");
        assertEquals("ab", sc.nextJsonString());
        int colAfterFirst = sc.col;
        int posAfterFirst = sc.pos;
        assertEquals(5, colAfterFirst);
        assertEquals(4, posAfterFirst);
        sc.match(',');
        assertEquals("cd", sc.nextJsonString());
        // match 消费 1 字符；第二个串消费 c/d/闭引号 3 字符；收尾 next() 命中 EOF 只推进 pos 不再结算 col
        assertEquals(colAfterFirst + 1 + 3, sc.col);
        assertEquals(posAfterFirst + 1 + 4, sc.pos);
        assertTrue(sc.isEnd());

        // 慢路径参考：同一逻辑内容在含转义时走慢路径，内容与消费完整性应一致
        // （源串因 \u0064 占 6 个源字符而更长，故末位 pos 为 14）
        sc = TextScanner.fromString(null, "\"ab\",\"c\\u0064\"");
        assertEquals("ab", sc.nextJsonString());
        sc.match(',');
        assertEquals("cd", sc.nextJsonString());
        assertTrue(sc.isEnd());
        assertEquals(14, sc.pos);

        // 跨行后第二个字符串的行号（match 内含 skipBlank，已停在开引号上）
        sc = TextScanner.fromString(null, "\"ab\",\n\"cd\"");
        assertEquals("ab", sc.nextJsonString());
        sc.match(',');
        assertEquals(2, sc.line);
        assertEquals("cd", sc.nextJsonString());
        assertEquals(2, sc.line);
        assertTrue(sc.isEnd());
    }

    @Test
    public void testNextJsonStringControlCharAndNewline() {
        // JSON 串内裸 CR/LF 报错
        TextScanner sc = TextScanner.fromString(null, "\"a\nb\"");
        NopException ex = assertThrows(NopException.class, sc::nextJsonString);
        assertEquals(ERR_SCAN_STRING_NOT_END.getErrorCode(), ex.getErrorCode());

        sc = TextScanner.fromString(null, "\"a\rb\"");
        assertThrows(NopException.class, sc::nextJsonString);
    }

    @Test
    public void testNextJsonStringUnterminated() {
        TextScanner sc = TextScanner.fromString(null, "\"abc");
        NopException ex = assertThrows(NopException.class, sc::nextJsonString);
        assertEquals(ERR_SCAN_STRING_NOT_END.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testNextJsonStringLongerThanBuffer() {
        // 超过常规缓冲区长度、无转义的长字符串仍走快路径且结果正确
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < 10000; i++) {
            sb.append("x");
        }
        sb.append("\"");
        TextScanner sc = TextScanner.fromString(null, sb.toString());
        String ret = sc.nextJsonString();
        assertEquals(10000, ret.length());
        assertEquals('x', ret.charAt(0));
        assertEquals('x', ret.charAt(9999));
        assertTrue(sc.isEnd());
    }

    @Test
    public void testNextLooseJsonStringUnchanged() {
        // Phase 2 Decision：loose 变体本次未加快路径，需确认行为不变（含引号后前瞻判定）
        // 注意 loose 语义：闭引号必须后跟终止符（,/://}/空白），EOF 前直接闭引号不终止
        TextScanner sc = TextScanner.fromString(null, "\"a\\nb\",\"c\" ");
        assertEquals("a\nb", sc.nextLooseJsonString());
        sc.match(',');
        assertEquals("c", sc.nextLooseJsonString());
        assertEquals(' ', sc.cur);

        // loose 特有：闭引号后跟非终止符时该引号被丢弃（既有行为，非内容保留）
        // 输入 "a"b" ,"：第二个引号后是 b（非终止符）→ 丢弃；第三个引号后是空格 → 终止
        sc = TextScanner.fromString(null, "\"a\"b\" ,\"");
        assertEquals("ab", sc.nextLooseJsonString());
        sc.skipBlank();
        sc.match(',');
        assertEquals('"', sc.cur);
    }

    @Test
    public void testNextNumberFastPathTypes() {
        // 快路径类型规则与 parseInteger 一致：int 范围内 Integer，否则 Long
        TextScanner sc = TextScanner.fromString(null, "123");
        assertEquals(Integer.valueOf(123), sc.nextNumber());
        assertTrue(sc.isEnd());

        sc = TextScanner.fromString(null, "-42");
        assertEquals(Integer.valueOf(-42), sc.nextNumber());
        assertTrue(sc.isEnd());

        sc = TextScanner.fromString(null, "2147483647");
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), sc.nextNumber());

        sc = TextScanner.fromString(null, "2147483648");
        assertEquals(Long.valueOf(2147483648L), sc.nextNumber());

        sc = TextScanner.fromString(null, "-2147483648");
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), sc.nextNumber());

        sc = TextScanner.fromString(null, "-2147483649");
        assertEquals(Long.valueOf(-2147483649L), sc.nextNumber());

        sc = TextScanner.fromString(null, "9223372036854775807");
        assertEquals(Long.valueOf(Long.MAX_VALUE), sc.nextNumber());

        sc = TextScanner.fromString(null, "-9223372036854775808");
        assertEquals(Long.valueOf(Long.MIN_VALUE), sc.nextNumber());
    }

    @Test
    public void testNextNumberFallbackSemantics() {
        // 前导零被原逻辑拒绝（禁止 octal）
        TextScanner sc = TextScanner.fromString(null, "007");
        assertThrows(NopException.class, sc::nextInt);

        sc = TextScanner.fromString(null, "0x1F");
        assertEquals(31, sc.nextInt());

        sc = TextScanner.fromString(null, "12l");
        assertEquals(Long.valueOf(12L), sc.nextNumber());

        sc = TextScanner.fromString(null, "3f");
        assertEquals(Float.valueOf(3.0f), sc.nextNumber());

        sc = TextScanner.fromString(null, "4d");
        assertEquals(Double.valueOf(4.0), sc.nextNumber());

        sc = TextScanner.fromString(null, "1.5");
        assertEquals(Double.valueOf(1.5), sc.nextNumber());

        sc = TextScanner.fromString(null, "1e3");
        assertEquals(Double.valueOf(1000.0), sc.nextNumber());

        // 溢出 long 报错（快路径回落原逻辑后由其抛出）
        sc = TextScanner.fromString(null, "99999999999999999999");
        assertThrows(NopException.class, sc::nextNumber);
    }

    @Test
    public void testNextJsonStringAfterNumber() {
        // consumeDigits 曾不更新 pos，数字后的字符串快路径会切错区间——回归用例
        TextScanner sc = TextScanner.fromString(null, "123,\"abc\",456,\"def\"");
        assertEquals(123, sc.nextNumber());
        sc.match(',');
        assertEquals("abc", sc.nextJsonString());
        sc.match(',');
        assertEquals(456, sc.nextNumber());
        sc.match(',');
        assertEquals("def", sc.nextJsonString());
        assertTrue(sc.isEnd());
    }
}
