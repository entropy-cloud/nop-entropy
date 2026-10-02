/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestProxyConfig {

    private static List<String> list(String... items) {
        return Arrays.asList(items);
    }

    @Test
    public void testCompareListSortsByLengthThenLexicographic() {
        ProxyConfig config = new ProxyConfig();
        // 更短的列表排在前面（长度优先，与内容无关）
        assertTrue(config.compareList(list("a"), list("a", "b")) < 0);
        assertTrue(config.compareList(list("a", "b"), list("a")) > 0);
        assertTrue(config.compareList(list("b"), list("a", "c")) < 0);
        // 等长按元素逐位比较
        assertTrue(config.compareList(list("a", "b"), list("a", "c")) < 0);
        assertTrue(config.compareList(list("a", "c"), list("a", "b")) > 0);
        assertEquals(0, config.compareList(list("a", "b"), list("a", "b")));
    }

    @Test
    public void testSortNormalizesSetOrder() {
        ProxyConfig config = new ProxyConfig();
        Set<List<String>> input = new LinkedHashSet<>();
        input.add(list("b", "a"));
        input.add(list("a"));
        input.add(list("a", "b"));
        config.setProxyClasses(input);

        config.sort();
        // 排序后代理接口列表：单元素在前，其余按字典序
        assertEquals(3, config.getProxyClasses().size());
        assertEquals(list("a"), config.getProxyClasses().iterator().next());
    }

    @Test
    public void testMergeAddsAndIsNullOrSafe() {
        ProxyConfig base = new ProxyConfig();
        Set<List<String>> baseSet = new LinkedHashSet<>();
        baseSet.add(list("a"));
        base.setProxyClasses(baseSet);

        ProxyConfig other = new ProxyConfig();
        Set<List<String>> otherSet = new LinkedHashSet<>();
        otherSet.add(list("a"));
        otherSet.add(list("b"));
        other.setProxyClasses(otherSet);

        base.merge(other);
        assertEquals(2, base.getProxyClasses().size());
        // merge 不影响源配置
        assertEquals(2, other.getProxyClasses().size());

        // 合并空配置（未初始化 proxyClasses）是无操作
        ProxyConfig empty = new ProxyConfig();
        base.merge(empty);
        assertEquals(2, base.getProxyClasses().size());
    }

    @Test
    public void testRemoveSubtractsAndIsNullOrSafe() {
        ProxyConfig base = new ProxyConfig();
        Set<List<String>> baseSet = new LinkedHashSet<>();
        baseSet.add(list("a"));
        baseSet.add(list("b"));
        base.setProxyClasses(baseSet);

        ProxyConfig delta = new ProxyConfig();
        Set<List<String>> deltaSet = new LinkedHashSet<>();
        deltaSet.add(list("a"));
        delta.setProxyClasses(deltaSet);

        base.remove(delta);
        assertEquals(1, base.getProxyClasses().size());
        assertTrue(base.getProxyClasses().contains(list("b")));
        assertFalse(base.getProxyClasses().contains(list("a")));

        // delta 未初始化时 remove 是无操作
        base.remove(new ProxyConfig());
        assertEquals(1, base.getProxyClasses().size());

        // base 未初始化时 remove 无异常
        new ProxyConfig().remove(delta);
    }

    @Test
    public void testGetProxyClassesLazilyInitializes() {
        ProxyConfig config = new ProxyConfig();
        assertTrue(config.getProxyClasses().isEmpty());
        // 惰性初始化后可继续添加
        config.getProxyClasses().add(list("x"));
        assertEquals(1, config.getProxyClasses().size());
    }
}
