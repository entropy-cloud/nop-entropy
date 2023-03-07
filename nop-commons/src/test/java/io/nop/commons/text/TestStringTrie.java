/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestStringTrie {

    @Test
    public void testAdd() {
        StringTrie<String> trie = new StringTrie<String>();
        trie.add("abcd", "ABCD");

        trie.add("ab", "AB");
        trie.dump(System.out);
        trie.add("abce", "abce");
        trie.put("abcd", "ABCd");
        trie.dump(System.out);
        trie.add("abc", "abc");
        trie.dump(System.out);
        trie.add("a", "a");
        trie.add("ef", "ef");
        trie.add("cde", "cde");
        trie.add("cdkf", "cdkf");
        trie.dump(System.out);
        String l1 = trie.find("ab");
        String l2 = trie.find("abc");
        String l3 = trie.find("abcd");
        String l4 = trie.find("abce");
        String l5 = trie.find("abcde");
        assertEquals("AB", l1.toString());
        assertEquals("abc", l2.toString());
        assertEquals("ABCd", l3.toString());
        assertEquals("abce", l4.toString());
        assertNull(l5);

        assertEquals(4, trie.findNode("abcde", true).getKeyLength());
    }

    @Test
    public void testFindWithPrefix() {
        StringTrie<String> trie = new StringTrie<>();
        trie.add("/p/_info/", "ABCD");
        trie.add("/_bbs/", "ABCD");

        trie.add("/_info/", "abce");
        trie.dump(System.out);
        trie.add("/_info/ch1/", "abct");
        trie.dump(System.out);
        trie.add("/_info/ch2/", "abcv");
        StringBuilder sb = new StringBuilder();
        trie.dump(sb);
        System.out.println(sb);
        assertEquals("abct", trie.findWithPrefix("/_info/ch1/abc").toString());
        assertEquals("abcv", trie.find("/_info/ch2/").toString());
    }
}