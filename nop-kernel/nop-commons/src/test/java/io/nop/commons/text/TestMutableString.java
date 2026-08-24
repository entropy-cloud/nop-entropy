/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMutableString {

    /**
     * subSequence产生的实例 this.start > 0，用于验证坐标系转换
     */
    private MutableString shifted() {
        return new MutableString("0123456789").subSequence(2);
    }

    @Test
    public void testSubstring() {
        assertEquals("45", shifted().substring(2, 4));
        assertEquals("23", shifted().substring(0, 2));
        assertEquals("23456789", shifted().substring(0));
        assertEquals("", shifted().substring(3, 3));
        // start==0 时保持原有行为
        assertEquals("01", new MutableString("0123456789").substring(0, 2));
    }

    @Test
    public void testIndexOf() {
        // 子串恰好结束于limit时也应命中
        assertEquals(3, new MutableString("hello").indexOf("lo"));
        assertEquals(3, new MutableString("hello").indexOf("lo", 2));
        assertEquals(0, new MutableString("hello").indexOf("he"));
        assertEquals(-1, new MutableString("hello").indexOf("lo", 4));
        // start>0 时返回相对下标
        assertEquals(4, shifted().indexOf("67"));
    }

    @Test
    public void testReplaceString() {
        // 末尾出现的子串也要被替换
        assertEquals("helLA", new MutableString("hello").replace("lo", "LA").toString());
        assertEquals("xllo", new MutableString("hello").replace("he", "x").toString());
        assertEquals("abc", new MutableString("abc").replace("z", "y").toString());
    }

    @Test
    public void testInsert() {
        assertEquals("abXYc", new MutableString("abc").insert(2, "XY").toString());
        assertEquals("XYabc", new MutableString("abc").insert(0, "XY").toString());
        assertEquals("abcXY", new MutableString("abc").insert(3, "XY").toString());
        // start>0 的实例上按相对位置插入
        assertEquals("23XY456789", shifted().insert(2, "XY").toString());
    }

    @Test
    public void testDelete() {
        assertEquals("ac", new MutableString("abc").delete(1, 2).toString());
        assertEquals("c", new MutableString("abc").delete(0, 2).toString());
        assertEquals("abc", new MutableString("abc").delete(0, 0).toString());
        // 超出length的end被截断
        assertEquals("", new MutableString("abc").delete(0, 10).toString());
        // start>0 的实例上按相对下标删除：content="23456789"，删除[2,4)即"45"
        assertEquals("236789", shifted().delete(2, 4).toString());
        assertEquals("456789", shifted().delete(0, 2).toString());
    }

    @Test
    public void testDeleteCharAt() {
        assertEquals("ac", new MutableString("abc").deleteCharAt(1).toString());
        assertEquals("2356789", shifted().deleteCharAt(2).toString());
    }

    @Test
    public void testReplaceRange() {
        assertEquals("aXYdef", new MutableString("abcdef").replace(1, 3, "XY").toString());
        assertEquals("XYdef", new MutableString("abcdef").replace(0, 3, "XY").toString());
        assertEquals("abcXY", new MutableString("abcdef").replace(3, 6, "XY").toString());
        // start>0 的实例
        assertEquals("2XY56789", shifted().replace(1, 3, "XY").toString());
    }

    @Test
    public void testDeleteWhitespace() {
        assertEquals("abc", new MutableString("a b\tc").deleteWhitespace().toString());
        // start>0 的实例（此前会因坐标系混用而错乱/越界）
        assertEquals("23456789", shifted().deleteWhitespace().toString());
    }
}
