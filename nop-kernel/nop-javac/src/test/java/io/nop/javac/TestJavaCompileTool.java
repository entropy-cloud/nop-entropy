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
import io.nop.javac.jdk.JavaSourceCode;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @TempDir
    static Path tempDir;

    @Test
    public void testSaveGeneratedBytesMatchInMemoryBytes() {
        // saveGenerated 是 GenAopProxy / TestAop 把 AOP 产物落盘的唯一路径，
        // 修复前无任何断言覆盖"落盘字节 == 内存字节"，回写被截断/错位都不会被发现
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        String className = "demo._GenSave";
        JavaCompileResult result = compiler.compile(className,
                "package demo; public class _GenSave { public int f() { return 7; } }",
                JdkJavaCompiler.getDefaultClassPaths());
        assertTrue(result.isSuccess());

        File outDir = tempDir.resolve("save-generated").toFile();
        result.saveGenerated(outDir);

        File classFile = new File(new File(outDir, "demo"), "_GenSave.class");
        assertTrue(classFile.isFile(), "saveGenerated 必须按包路径写出 .class: " + classFile);

        byte[] fromDisk = FileHelper.readBytes(classFile);
        assertArrayEquals(result.getGeneratedClassBytes(className), fromDisk);
    }

    @Test
    public void testGeneratedClassNamesAndBytesAreDirectlyObservable() {
        // 修复前无 getGeneratedClassNames() 非空、无 getGeneratedClassBytes() 内容的直接断言
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        String className = "demo._GenAccess";
        JavaCompileResult result = compiler.compile(className,
                "package demo; public class _GenAccess { public int f() { return 1; } }",
                JdkJavaCompiler.getDefaultClassPaths());
        assertTrue(result.isSuccess());

        assertTrue(result.getGeneratedClassNames().contains(className),
                "产物名集合应含被编译类: " + result.getGeneratedClassNames());

        byte[] bytes = result.getGeneratedClassBytes(className);
        assertNotNull(bytes);
        // class 文件魔数 CA FE BA BE，偏移 6-7 是大端 major version
        assertEquals(0xCA, bytes[0] & 0xff);
        assertEquals(0xFE, bytes[1] & 0xff);
        assertEquals(0xBA, bytes[2] & 0xff);
        assertEquals(0xBE, bytes[3] & 0xff);

        assertNull(result.getGeneratedClassBytes("demo._NeverGenerated"));
    }

    @Test
    public void testDiagnosticSourceFieldsArePopulatedForReadableSource() throws Exception {
        // 修复前源码不可读时按占位符长度(27)裁剪位置并切片，产出误导性 Code of concern。
        // 本用例锁定内存输入路径（源码可读）下这些字段确实被填充，是 B2 修复的正常路径护栏
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        // 类型不兼容错误的诊断带真实 start<end 高亮区间（实测 start=pos=43, end=44），
        // 可据以断言 codeOfConcern 被正确填充；纯符号错误实测 start==pos==end，区间为空，不适合做断言面
        JavaCompileResult result = compiler.compile("demo._GenDiag",
                "package demo; public class _GenDiag { String s = 1; }",
                JdkJavaCompiler.getDefaultClassPaths());
        assertFalse(result.isSuccess());

        JavaCompileResult.CompileResultMessage message = firstErrorMessage(result);
        assertEquals("ERROR", message.typeOfProblem);
        assertEquals(1, message.lineNumber);
        assertTrue(message.columnNumber > 0);
        assertTrue(message.sourceCode.contains("String s = 1;"),
                "内存输入路径源码应可读，实际: " + message.sourceCode);
        assertTrue(message.textHighlightPos_problemEnd > message.textHighlightPos_problemStart,
                "该诊断应有非空高亮区间");
        assertFalse(message.codeOfConcern.isEmpty(), "可读源码路径下 codeOfConcern 不得为空");
        assertFalse(message.codeOfConcernLong.isEmpty(), "可读源码路径下 codeOfConcernLong 不得为空");
        assertTrue(message.multiLineMessage.contains("Code of concern"));
    }

    @Test
    public void testErrorMessageStillReadableAsString() throws Exception {
        // getErrorMessage() / toString() 的既有语义不得因结构化诊断改造而破坏
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileResult result = compiler.compile("demo._GenErrMsg",
                "package demo; public class _GenErrMsg { undefinedSymbol(); }",
                JdkJavaCompiler.getDefaultClassPaths());
        assertFalse(result.isSuccess());

        String errorMessage = result.getErrorMessage();
        assertFalse(errorMessage.isEmpty());
        // getErrorMessage() 复用 toString()，其形态是每条诊断的 toStringForDebugging()；
        // 断言该稳定结构而非诊断文案本身（javac 文案随 JDK 版本与 locale 变化）
        assertTrue(errorMessage.contains("Message:"), errorMessage);
        assertTrue(errorMessage.contains("line: "), errorMessage);
        assertEquals(result.toString(), errorMessage);
    }

    @Test
    public void testGenAopProxyCallSequenceReplica() {
        // 复刻 GenAopProxy.generate() 与 TestAop 的实际调用序列。
        // nop-codegen → nop-core → nop-javac 构成依赖环，无法在本模块直接调用 GenAopProxy，
        // 故只能复刻序列；真实链路由 nop-ioc 的 exec:aop 构建产物承担（Phase 1 Exit Criteria 接线验证）
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();

        List<JavaSourceCode> sources = List.of(
                new JavaSourceCode("demo._AopGenFirst", "package demo; public class _AopGenFirst { public int f() { return 1; } }"),
                new JavaSourceCode("demo._AopGenSecond", "package demo; public class _AopGenSecond { public int f() { return 2; } }"));
        JavaCompileResult result = compiler.compile(sources, classPaths);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("", result.getErrorMessage());

        File outDir = tempDir.resolve("save-generated").toFile();
        result.saveGenerated(outDir);

        assertTrue(new File(new File(outDir, "demo"), "_AopGenFirst.class").isFile());
        assertTrue(new File(new File(outDir, "demo"), "_AopGenSecond.class").isFile());
    }

    @Test
    public void testGenAopProxyFailureSequenceReplica() {
        // 同序列的失败面：GenAopProxy 依据 isSuccess() 抛 ERR_GEN_AOP_PROXY_FAIL，
        // 其 param 来自 getErrorMessage()，故失败时该串必须非空且可读
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();

        JavaCompileResult result = compiler.compile(List.of(
                new JavaSourceCode("demo._AopGenBroken", "package demo; public class _AopGenBroken { ")),
                classPaths);

        assertFalse(result.isSuccess());
        assertFalse(result.getErrorMessage().isEmpty());
    }

    private static JavaCompileResult.CompileResultMessage firstErrorMessage(JavaCompileResult result) throws Exception {
        // messages 列表当前无访问器（Phase 3 才补），此处经 getErrorMessage() 反射读取单条消息
        java.lang.reflect.Field field = JavaCompileResult.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<JavaCompileResult.CompileResultMessage> messages =
                (java.util.List<JavaCompileResult.CompileResultMessage>) field.get(result);
        assertFalse(messages.isEmpty(), "失败编译必须产出诊断消息");
        for (JavaCompileResult.CompileResultMessage m : messages) {
            if ("ERROR".equals(m.typeOfProblem))
                return m;
        }
        return messages.get(0);
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
