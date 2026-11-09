/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import org.junit.jupiter.api.Test;

import io.nop.xlang.exec.XLangSemantics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * plan 2548 R4-14（C9-004）静态方法集合缓存载体钉。
 */
public class TestXlangStaticMethodCache {

    /**
     * pin9004a：同一 (className,funcName) 两次解析 → 第二次命中缓存
     * （可观测路径 = assertSame 实例同一性；「类加载计数」对已加载类不可计量，故弃用）。
     */
    @Test
    public void pin9004a_secondCallHitsCache() {
        Object c1 = XLangSemantics.resolveStaticMethodCollection("java.lang.String", "valueOf");
        Object c2 = XLangSemantics.resolveStaticMethodCollection("java.lang.String", "valueOf");
        assertNotNull(c1, "java.lang.String.valueOf 应可解析");
        assertSame(c1, c2, "第二次调用应命中缓存返回同一实例");
    }

    /** pin9004c：ClassNotFoundException 仍返回 null（不抛），失败不缓存（可重试语义保持）。 */
    @Test
    public void pin9004c_classNotFoundReturnsNull() {
        assertNull(XLangSemantics.resolveStaticMethodCollection("io.nop.not.ExistClass", "foo"));
        assertNull(XLangSemantics.resolveStaticMethodCollection("io.nop.not.ExistClass", "foo"));
    }
}
