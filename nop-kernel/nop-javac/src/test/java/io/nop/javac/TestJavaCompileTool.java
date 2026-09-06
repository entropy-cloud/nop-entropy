/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJavaCompileTool {
    @Test
    public void testParse() {
        File file = FileHelper.getClassPathFile("_FunctionDeclaration.java");
        String text = FileHelper.readText(file, null);

        IJavaParseResult result = JavaCompileTool.instance().parseJavaSource(null, text);
        System.out.println(result.getFormattedSource());

        String source = JavaCompileTool.instance().formatJavaSource(null, text);
        assertEquals(source, result.getFormattedSource());
    }

    @Test
    public void testError() {
        File file = FileHelper.getClassPathFile("_CompileError.java");
        String text = FileHelper.readText(file, null);

        try {
            JavaCompileTool.instance().parseJavaSource(null, text);
            assertTrue(false);
        } catch (NopException e) {
            System.out.println(e);
            assertEquals(6, e.getErrorLocation().getLine());
        }
    }

    @Test
    public void defineClass() {
        DynamicURLClassLoader loader = new DynamicURLClassLoader("a", this.getClass().getClassLoader());

    }

    @Test
    public void testLoadMissingGeneratedClassThrowsCNFE() throws Exception {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        String code = "package demo;\npublic class _GenFoo { public int f() { return 1; } }";
        JavaCompileResult result = compiler.compile("demo._GenFoo", code, JdkJavaCompiler.getDefaultClassPaths());
        assertTrue(result.isSuccess());

        // 修复前：ClassLoaderImpl.findClass 未命中返回 null，违反 ClassLoader 契约，
        // getGeneratedClass 拿到 null 不抛异常（或以 NPE 形态崩溃），丢失"类不存在"语义
        NopException e = assertThrows(NopException.class, () -> result.getGeneratedClass("demo.NotGenerated"));
        assertTrue(e.getCause() instanceof ClassNotFoundException, String.valueOf(e.getCause()));

        // 已生成的类正常加载
        Class<?> clazz = result.getGeneratedClass("demo._GenFoo");
        assertEquals("demo._GenFoo", clazz.getName());
    }

    @Test
    public void testGetErrorDetailShortMessageDoesNotThrow() {
        // janino 的 getMessage() = locStr + ": " + rawMessage，正常路径剥离 ": " 前缀。
        // 极短 message（空/单字符）不应触发越界
        Location loc = new Location("F.java", 3, 5);
        assertEquals("", JavaCompileTool.instance().getErrorDetail(
                new CompileException("", loc)));
        assertEquals("x", JavaCompileTool.instance().getErrorDetail(
                new CompileException("x", loc)));
        assertEquals("detail", JavaCompileTool.instance().getErrorDetail(
                new CompileException("detail", loc)));
    }

    @Test
    public void testCompileAcceptsCurrentJdkSyntax() {
        // 修复前：-source/-target 硬编码 1.8，生成代码使用 var（Java 10+）等新语法时编译失败
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        String code = "package demo;\npublic class _GenVar { public int f() { var x = 1; return x; } }";
        JavaCompileResult result = compiler.compile("demo._GenVar", code, JdkJavaCompiler.getDefaultClassPaths());

        assertTrue(result.isSuccess());
        Class<?> clazz = result.getGeneratedClass("demo._GenVar");
        assertEquals("demo._GenVar", clazz.getName());
    }

    @Test
    public void testCompileInvalidClassNameThrowsNopException() {
        // 修复前：URI 构造异常被静默吞掉，以 null URI 继续构造 SimpleJavaFileObject，
        // 错误推迟到 javac 内部且不带类名信息
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        String code = "package demo;\npublic class _GenBad { }";

        NopException e = assertThrows(NopException.class,
                () -> compiler.compile("demo. Bad", code, JdkJavaCompiler.getDefaultClassPaths()));
        assertEquals("demo. Bad", e.getParam("className"));
    }
}
