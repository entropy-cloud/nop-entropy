/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.loop;

import io.nop.core.model.loop.impl.NestedLoopBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static io.nop.commons.util.objects.Pair.pair;
import static io.nop.core.CoreConstants.LOOP_ROOT_VAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNestedLoop {
    INestedLoop buildLoop() {
        NestedLoopBuilder builder = new NestedLoopBuilder();
        builder.defineGlobalVar("a", "a");
        builder.defineGlobalVar("b", "b");
        builder.defineGlobalVar("c", Collections.emptyList());
        builder.defineGlobalVar("d", Arrays.asList("d1", "d2"));
        builder.defineLoopVar("aa", "a", a -> Arrays.asList(a + "1", a + "2"));
        builder.defineLoopVar("bb", "b", b -> Arrays.asList(b + "1", b + "2"));
        builder.defineLoopVar("aaa", "aa", aa -> aa + "1");
        builder.defineLoopVar("bbb", "bb", bb -> Arrays.asList(bb + "1", bb + "2"));
        builder.defineLoopVar("dd", "d", d -> Arrays.asList(d + "1", d + "2"));
        return builder.build();
    }

    @Test
    public void testRoot() {
        INestedLoop loop = buildLoop();
        List<Object> list = loop.toList();
        assertEquals(1, list.size());
        assertEquals(null, list.get(0));

        list = loop.loopForVar(null).toList();
        assertEquals(1, list.size());
        assertEquals(null, list.get(0));

        list = loop.loopForVar("a").toList();
        assertEquals(1, list.size());
        assertEquals("a", list.get(0));

        list = loop.loopForVar("a").loopForVar("b").toList();
        assertEquals(1, list.size());
        assertEquals("b", list.get(0));

        list = loop.loopForVar("c").toList();
        assertTrue(list.isEmpty());

        list = loop.loopForVar("a").loopForVar("c").toList();
        assertTrue(list.isEmpty());

        list = loop.loopForVar("c").loopForVar("a").toList();
        assertTrue(list.isEmpty());
    }

    @Test
    public void testNested() {
        INestedLoop loop = buildLoop();
        List<Object> list;
        list = loop.loopForVar("a").loopForVar("aa").toList();
        assertEquals("[a1, a2]", list.toString());
        assertTrue(loop.hasVar("a"));

        list = loop.loopForVar("a").loopForVar("aa").loopForVar("a").toList();
        assertEquals("[a, a]", list.toString());

        list = loop.loopForVar("aa").toList();
        assertEquals("[a1, a2]", list.toString());

        list = loop.loopForVar("aa").loopForVar("a").toList();
        assertEquals("[a, a]", list.toString());

        list = loop.loopForVar("aa").loopForVar("b").toList();
        assertEquals("[b, b]", list.toString());
    }

    @Test
    public void testNestedNested() {
        INestedLoop loop = buildLoop();
        List<Object> list;
        list = loop.loopForVar("aa").loopForVar("bb").toList();
        assertEquals("[b1, b2, b1, b2]", list.toString());

        Iterator<INestedLoopVar> subLoop = loop.loopForVar("bbb").loopForVar("aaa").iterator();
        INestedLoopVar var = subLoop.next();

        assertEquals(Arrays.asList(pair("aaa", "a11"), pair("aa", "a1"), pair("a", "a"), pair("bbb", "b11"),
                pair("bb", "b1"), pair("b", "b"), pair(LOOP_ROOT_VAR, null)), var.getLoopValues());

        var = subLoop.next();
        assertEquals(Arrays.asList(pair("aaa", "a21"), pair("aa", "a2"), pair("a", "a"), pair("bbb", "b11"),
                pair("bb", "b1"), pair("b", "b"), pair(LOOP_ROOT_VAR, null)), var.getLoopValues());

        var = subLoop.next();
        assertEquals(Arrays.asList(pair("aaa", "a11"), pair("aa", "a1"), pair("a", "a"), pair("bbb", "b12"),
                pair("bb", "b1"), pair("b", "b"), pair(LOOP_ROOT_VAR, null)), var.getLoopValues());

        list = loop.loopForVar("bbb").loopForVar("aaa").toList();
        assertEquals("[a11, a21, a11, a21, a11, a21, a11, a21]", list.toString());

        list = loop.loopForVar("bb").loopForVar("aaa").toList();
        assertEquals("[a11, a21, a11, a21]", list.toString());

        list = loop.loopForVar("bb").loopForVar("bbb").toList();
        assertEquals("[b11, b12, b21, b22]", list.toString());

        list = loop.loopForVar("b").loopForVar("bb").loopForVar("bbb").toList();
        assertEquals("[b11, b12, b21, b22]", list.toString());

    }

    @Test
    public void testMix() {
        INestedLoop loop = buildLoop();
        List<Object> list;

        list = loop.loopForVar("bbb").loopForVar("b").loopForVar("bbb").toList();
        assertEquals("[b11, b12, b21, b22]", list.toString());

        list = loop.loopForVar("bb").loopForVar("b").loopForVar("bbb").toList();
        assertEquals("[b11, b12, b21, b22]", list.toString());
        list = loop.loopForVar("bbb").toList();
        assertEquals("[b11, b12, b21, b22]", list.toString());

        list = loop.loopForVar("bbb").loopForVar("b").loopForVar("bbb").loopForVar("bbb").toList();
        assertEquals("[b11, b12, b21, b22]", list.toString());
    }

    @Test
    public void testRootList() {
        INestedLoop loop = buildLoop();
        List<Object> list;

        list = loop.loopForVar("dd").toList();
        assertEquals("[d11, d12, d21, d22]", list.toString());

        list = loop.loopForVar("b").loopForVar("dd").toList();
        assertEquals("[d11, d12, d21, d22]", list.toString());

        list = loop.loopForVar("b").loopForVar("dd").loopForVar("d").loopForVar("dd").toList();
        assertEquals("[d11, d12, d21, d22]", list.toString());
    }
}