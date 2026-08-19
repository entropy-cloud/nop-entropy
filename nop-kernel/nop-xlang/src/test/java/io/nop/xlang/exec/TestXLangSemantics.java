package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 共享语义 helper 基座 {@link XLangSemantics} 的 focused 回归：
 * 重点覆盖跨 funcName 分派不串缓存（回归 2026-08-20：单一 static handle 的类名缓存在同 JVM
 * 内跨函数名污染——先解析 charAt 后 toUpperCase 命中错误函数），以及自解释器内联分支提取的
 * Plus 完整语义。
 */
public class TestXLangSemantics {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testCrossFuncNameDispatchNotPollutedByClassCache() {
        IEvalScope scope = new EvalScopeImpl(new LinkedHashMap<>());
        SourceLocation loc = SourceLocation.fromLine("test.x", 1);

        assertEquals(Character.valueOf('x'), XLangSemantics.invokeObjMethod(loc, "\"x\".charAt(0)", "x", "charAt",
                new Object[]{0}, scope));

        // 同一接收者类（String）、不同 funcName：不得命中上一函数名的类级缓存
        assertEquals("HELLO", XLangSemantics.invokeObjMethod(loc, "\"hello\".toUpperCase()", "hello",
                "toUpperCase", new Object[0], scope));

        // 交替调用保持稳定
        assertEquals("hello", XLangSemantics.invokeObjMethod(loc, "\"HELLO\".toLowerCase()", "HELLO",
                "toLowerCase", new Object[0], scope));
        assertEquals("HELLO", XLangSemantics.invokeObjMethod(loc, "\"hello\".toUpperCase()", "hello",
                "toUpperCase", new Object[0], scope));
    }

    @Test
    public void testPlusFullSemantics() {
        // String 拼接分支（自 PlusExecutable 内联提取）+ 数值路径（MathHelper.add）
        assertEquals("ab!", XLangSemantics.plus("a", "b!"));
        assertEquals("3x", XLangSemantics.plus(3, "x"));
        assertEquals("x3", XLangSemantics.plus("x", 3));
        assertEquals(7, XLangSemantics.plus(1, 6));
        assertEquals(7.5, XLangSemantics.plus(1.5, 6));
    }

    @Test
    public void testGuardNotNull() {
        SourceLocation loc = SourceLocation.fromLine("test.x", 3);
        assertEquals("v", XLangSemantics.guardNotNull(loc, "v!", "v"));
        try {
            XLangSemantics.guardNotNull(loc, "null!", null);
            throw new AssertionError("expected NopEvalException");
        } catch (io.nop.api.core.exceptions.NopEvalException e) {
            assertEquals("nop.err.xlang.exec.value-not-allow-null", e.getErrorCode());
            assertEquals(loc, e.getErrorLocation());
        }
    }
}
