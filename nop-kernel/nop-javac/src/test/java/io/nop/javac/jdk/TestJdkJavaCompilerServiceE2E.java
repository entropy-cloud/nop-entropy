/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 编译服务的**能力验收基准**：模拟其最小调用形态——
 * 按 effective-pom 等价语义设置选项 → 编译一组磁盘源文件 → 从输出目录读取产物 →
 * 用结构化诊断断言结果。
 *
 * <p>本测试即服务后续落地时的验收基准，覆盖选项设置 → 编译 → 产物落盘 → 结构化诊断全链路。
 */
public class TestJdkJavaCompilerServiceE2E {

    @TempDir
    static Path tempDir;

    private File writeSource(String relative, String code) throws Exception {
        Path p = tempDir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, code.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    private File out(String name) {
        return tempDir.resolve(name).toFile();
    }

    @Test
    public void testServiceMinimalCallShape() throws Exception {
        // ---- 1. 选项设置：等价 effective-pom 的 release + classpath + 输出目录 + 隐式编译策略 ----
        // release 17 = 本仓按 execution 的默认 release；隐式编译显式 none =
        // 防止源码路径上的隐式文件写出 class 覆盖生成代码产物
        JavaCompileOptions options = new JavaCompileOptions()
                .setRelease("17")
                .setClassPaths(List.of(System.getProperty("java.class.path").split(File.pathSeparator)))
                .setOutputDir(out("service-out"))
                .setImplicitPolicy(JavaCompileOptions.IMPLICIT_NONE)
                .setSourcePath(List.of(tempDir.toFile()));

        // ---- 2. 编译一组磁盘源文件 ----
        File alpha = writeSource("svc/Alpha.java",
                "package svc; public class Alpha { public String name() { return \"alpha\"; } }");
        File beta = writeSource("svc/Beta.java",
                "package svc; public class Beta { public String name() { return \"beta\"; } }");
        File referrer = writeSource("svc/Referrer.java",
                "package svc; public class Referrer { public String both() { return new Alpha().name() + new Beta().name(); } }");

        JavaFileCompileResult result = new JdkJavaCompiler()
                .compileFiles(List.of(referrer, alpha, beta), options);

        // ---- 3. 结构化诊断断言（成功路径应零诊断）----
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(result.getDiagnostics().isEmpty(), "成功编译不应产出诊断，实际: " + result.getDiagnostics());
        assertTrue(result.getErrors().isEmpty());
        assertEquals("", result.getErrorMessage());

        // ---- 4. 从输出目录读取产物 ----
        File outDir = result.getOutputDir();
        assertEquals(out("service-out"), outDir);
        for (String className : List.of("Alpha", "Beta", "Referrer")) {
            assertTrue(new File(outDir, "svc/" + className + ".class").isFile(),
                    "产物必须落在输出目录预期位置: " + className);
        }

        // ---- 5. 产物可加载且行为正确（对齐 --release 语义）----
        assertEquals(61, majorVersion(new File(outDir, "svc/Alpha.class")), "--release 17 的 major version");

        try (URLClassLoader loader = new URLClassLoader(new URL[]{outDir.toURI().toURL()}, null)) {
            Class<?> referrerClass = loader.loadClass("svc.Referrer");
            Object instance = referrerClass.getDeclaredConstructor().newInstance();
            assertEquals("alphabeta", referrerClass.getMethod("both").invoke(instance));
        }
    }

    @Test
    public void testServiceCallShapeReportsStructuredDiagnosticsOnFailure() throws Exception {
        JavaCompileOptions options = new JavaCompileOptions()
                .setRelease("17")
                .setClassPaths(List.of(System.getProperty("java.class.path").split(File.pathSeparator)))
                .setOutputDir(out("service-out-fail"));

        File broken = writeSource("svcfail/Broken.java", "package svcfail; public class Broken { String s = 1; }");

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(broken), options);

        assertFalse(result.isSuccess());
        // 服务需要的是结构化反馈，不是一个待解析的字符串
        List<JavaDiagnostic> errors = result.getErrors();
        assertFalse(errors.isEmpty(), "失败必须产出可结构化读出的诊断");

        JavaDiagnostic error = errors.get(0);
        assertEquals("ERROR", error.getSeverity());
        assertEquals(JavaDiagnostic.SOURCE_JAVAC, error.getSource());
        assertTrue(error.getFile().startsWith("file:"), "服务据此定位源文件: " + error.getFile());
        assertTrue(error.getFile().endsWith("Broken.java"), error.getFile());
        assertEquals(1, error.getLine());
        assertTrue(error.getColumn() > 0);
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isEmpty());

        // 无产物可读
        assertFalse(new File(out("service-out-fail"), "svcfail/Broken.class").isFile());
    }

    @Test
    public void testServiceCallShapeRejectsSilentFailureShapes() throws Exception {
        // 前置自校验面：这些形态 javac 全部静默接受，服务侧必须拿到显式失败
        File src = writeSource("svcpre/Main.java", "package svcpre; public class Main { public int f() { return 1; } }");

        // 非法编码名
        JavaFileCompileResult badEncoding = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions()
                        .setRelease("17")
                        .setOutputDir(out("svc-badenc"))
                        .setEncoding("NO-SUCH-CHARSET"));
        assertFalse(badEncoding.isSuccess());
        assertEquals("nop.err.javac.invalid-encoding", badEncoding.getErrors().get(0).getErrorCode());

        // 空输出目录
        JavaFileCompileResult emptyOut = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setRelease("17").setOutputDir(new File("")));
        assertFalse(emptyOut.isSuccess());
        assertEquals("nop.err.javac.invalid-output-dir", emptyOut.getErrors().get(0).getErrorCode());

        // 不存在的源码路径
        JavaFileCompileResult missingSp = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions()
                        .setRelease("17")
                        .setOutputDir(out("svc-missingsp"))
                        .setSourcePath(List.of(tempDir.resolve("nope").toFile())));
        assertFalse(missingSp.isSuccess());
        assertEquals("nop.err.javac.invalid-option-value", missingSp.getErrors().get(0).getErrorCode());
    }

    private static int majorVersion(File classFile) throws IOException {
        byte[] bytes = io.nop.commons.util.FileHelper.readBytes(classFile);
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }
}