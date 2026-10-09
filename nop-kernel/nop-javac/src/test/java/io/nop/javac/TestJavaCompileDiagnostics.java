/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JavaDiagnostic;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化诊断访问面的测试。
 *
 * <p>本类位于 {@code io.nop.javac}（与 {@code io.nop.javac.jdk} **不同包**），
 * 用于验证新增诊断面可被模块外调用方使用——即验证可见性/可访问性，不只是类型存在。
 */
public class TestJavaCompileDiagnostics {

    @TempDir
    static Path tempDir;

    // ---------- 内存编译路径的结构化诊断 ----------

    @Test
    public void testMemoryCompileExposesStructuredDiagnostics() {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._DiagA", "package demo; public class _DiagA { String s = 1; }",
                JdkJavaCompiler.getDefaultClassPaths());

        assertFalse(result.isSuccess());
        List<JavaDiagnostic> diagnostics = result.getDiagnostics();
        assertFalse(diagnostics.isEmpty(), "失败编译必须产出结构化诊断");

        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals("ERROR", error.getSeverity());
        assertEquals(JavaDiagnostic.SOURCE_JAVAC, error.getSource());
        assertTrue(error.isError());
        assertEquals(1, error.getLine(), "单行源码的错误应在第 1 行");
        assertTrue(error.getColumn() > 0);
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isEmpty());
        // javac 不产出本平台错误码
        assertEquals(null, error.getErrorCode());
    }

    @Test
    public void testMemoryDiagnosticsFileIdUsesStringUriForm() {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._DiagB", "package demo; public class _DiagB { undefinedSym(); }",
                JdkJavaCompiler.getDefaultClassPaths());

        assertFalse(result.isSuccess());
        JavaDiagnostic error = result.getErrors().get(0);
        // 内存输入沿用 string:/// URI 形态（既有行为，不改）
        assertTrue(error.getFile().startsWith("string:"),
                "内存输入诊断应保持 string:/// 形态，实际: " + error.getFile());
        assertTrue(error.getFile().endsWith("demo/_DiagB.java"), error.getFile());
    }

    @Test
    public void testDiagnosticsListIsReadOnlyView() {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._DiagC", "package demo; public class _DiagC { undefinedSym(); }",
                JdkJavaCompiler.getDefaultClassPaths());

        List<JavaDiagnostic> diagnostics = result.getDiagnostics();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> diagnostics.add(new JavaDiagnostic("ERROR", "x", null, 0, 0, "m", null)));
    }

    @Test
    public void testGetMessagesReadOnlyViewPreserved() {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._DiagD", "package demo; public class _DiagD { String s = 1; }",
                JdkJavaCompiler.getDefaultClassPaths());

        List<JavaCompileResult.CompileResultMessage> messages = result.getMessages();
        assertFalse(messages.isEmpty(), "getMessages 应返回 javac 原始诊断");
        JavaCompileResult.CompileResultMessage message = messages.get(0);
        assertEquals("ERROR", message.typeOfProblem);
        assertTrue(message.sourceCode.contains("String s = 1;"), "可读路径应带源码片段");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> messages.add(message));
    }

    @Test
    public void testExistingStringSurfacesUnchanged() {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._DiagE", "package demo; public class _DiagE { String s = 1; }",
                JdkJavaCompiler.getDefaultClassPaths());

        assertFalse(result.isSuccess());
        // 新增结构化面不得成为唯一途径：既有字符串面必须继续可用且语义不变
        assertFalse(result.getErrorMessage().isEmpty());
        assertEquals(result.toString(), result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Message:"));
    }

    @Test
    public void testSuccessCaseExposesEmptyDiagnostics() {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._DiagF", "package demo; public class _DiagF { public int f() { return 1; } }",
                JdkJavaCompiler.getDefaultClassPaths());

        assertTrue(result.isSuccess());
        assertTrue(result.getDiagnostics().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }
}