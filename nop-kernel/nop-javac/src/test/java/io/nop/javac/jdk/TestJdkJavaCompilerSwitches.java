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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隐式编译策略 / 注解处理 / 生成源目录 / 模块路径 / 自定义透传的"生效 + 非法取值"对照测试。
 *
 * <p>"生效"的断言面一律是**输出目录内容或结构化诊断**——实测这些开关的很多非法取值
 * javac 静默接受（call()==true、collector 零诊断），诊断不是可靠断言面。
 */
public class TestJdkJavaCompilerSwitches {

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

    // ---------- 隐式编译策略：隐式 class 只可从输出目录内容观测 ----------

    @Test
    public void testImplicitNoneSilentlySkipsImplicitClass() throws Exception {
        // 源码路径上放一个不被显式编译的类，靠源码被隐式引用拉进来
        writeSource("impl/Implicit.java", "package impl; public class Implicit { public int v() { return 1; } }");
        File main = writeSource("impl/Main.java",
                "package impl; public class Main { public int f() { return new Implicit().v(); } }");

        // sourcepath 是源码根（包目录的父目录），不是包目录本身
        File sourcePathDir = tempDir.toFile();
        File outNone = out("out-implicit-none");
        JavaCompileOptions options = new JavaCompileOptions()
                .setOutputDir(outNone)
                .setSourcePath(List.of(sourcePathDir))
                .setImplicitPolicy(JavaCompileOptions.IMPLICIT_NONE);

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main), options);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(new File(outNone, "impl/Main.class").isFile(), "显式编译的类必须产出");
        assertFalse(new File(outNone, "impl/Implicit.class").isFile(),
                "-implicit:none 应静默不生成隐式 class");
        // 实测：-implicit:none 是静默跳过，不是报错
        assertTrue(result.getErrors().isEmpty(), "-implicit:none 不得产生错误诊断");
    }

    @Test
    public void testImplicitClassProducesImplicitClass() throws Exception {
        writeSource("impl2/Implicit.java", "package impl2; public class Implicit { public int v() { return 1; } }");
        File main = writeSource("impl2/Main.java",
                "package impl2; public class Main { public int f() { return new Implicit().v(); } }");

        File sourcePathDir = tempDir.toFile();
        File outClass = out("out-implicit-class");
        JavaCompileOptions options = new JavaCompileOptions()
                .setOutputDir(outClass)
                .setSourcePath(List.of(sourcePathDir))
                .setImplicitPolicy(JavaCompileOptions.IMPLICIT_CLASS);

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main), options);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(new File(outClass, "impl2/Main.class").isFile());
        assertTrue(new File(outClass, "impl2/Implicit.class").isFile(),
                "-implicit:class 应产出隐式 class");
    }

    @Test
    public void testInvalidImplicitPolicyRejectedBeforeJavac() throws Exception {
        File main = writeSource("impl3/Main.java", "package impl3; public class Main { public int f() { return 1; } }");

        // 实测 javac 对 -implicit:bogus 在 getTask 阶段抛 IllegalArgumentException；
        // 本模块在 getTask 之前给出带错误码的结构化诊断
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-implicit-bad"))
                        .setImplicitPolicy("bogus"));

        assertFalse(result.isSuccess());
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals("nop.err.javac.invalid-option-value", error.getErrorCode());
        assertTrue(error.getMessage().contains("-implicit"), error.getMessage());
        assertTrue(error.getMessage().contains("bogus"), error.getMessage());
    }

    // ---------- 注解处理：显式关闭 vs 显式开启 ----------

    @Test
    public void testProcNoneDoesNotRunProcessor() throws Exception {
        File main = writeSource("proc1/Main.java", "package proc1; public class Main { public int f() { return 1; } }");
        File genDir = tempDir.resolve("gen-none").toFile();
        File outDir = out("out-proc-none");

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(outDir)
                        .setProcMode("none")
                        .setProcessorPath(List.of(testClassesDir()))
                        .setProcessors(List.of(MarkingProcessor.class.getName()))
                        .setGeneratedSourceDir(genDir));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertFalse(new File(genDir, "gen/Generated.java").exists(),
                "proc:none 下处理器不得生成源码");
        assertTrue(new File(outDir, "proc1/Main.class").isFile(), "主类仍应产出");
    }

    @Test
    public void testProcFullRunsExplicitlyNamedProcessor() throws Exception {
        File main = writeSource("proc2/Main.java", "package proc2; public class Main { public int f() { return 1; } }");
        File genDir = tempDir.resolve("gen-full").toFile();
        File outDir = out("out-proc-full");

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(outDir)
                        .setProcMode("full")
                        .setProcessorPath(List.of(testClassesDir()))
                        .setProcessors(List.of(MarkingProcessor.class.getName()))
                        .setGeneratedSourceDir(genDir));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(new File(genDir, "gen/Generated.java").isFile(),
                "proc:full + 显式 -processor 应生成源码，实际目录: " + listDir(genDir));
        // -s 必须生效：生成的 .java 不得落进 -d 输出目录
        assertFalse(new File(outDir, "gen/Generated.java").exists(),
                "给了 -s 后生成源码不得落进输出目录");
        assertTrue(new File(outDir, "proc2/Main.class").isFile());
    }

    @Test
    public void testGeneratedSourceDirPointingToRegularFileIsRejected() throws Exception {
        File main = writeSource("proc3/Main.java", "package proc3; public class Main { public int f() { return 1; } }");
        File plainFile = tempDir.resolve("gen-not-a-dir.txt").toFile();
        io.nop.commons.util.FileHelper.writeBytes(plainFile, "x".getBytes(StandardCharsets.UTF_8));

        // 实测 javac 对任意 -s 值（含指向普通文件的）：call()==true、零诊断、完全静默
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-gensrc-file"))
                        .setGeneratedSourceDir(plainFile));

        assertFalse(result.isSuccess(), "生成源目录指向普通文件必须被显式拒绝");
        assertEquals("nop.err.javac.invalid-output-dir", result.getErrors().get(0).getErrorCode());
    }

    /**
     * 处理器路径的断言面是**选项装配**，不是处理器是否被发现。
     *
     * <p>实测：javac 在处理器不在 {@code -processorpath} 上时会回退到**宿主 JVM 的
     * {@code java.class.path}** 去找处理器（把 -processorpath 指向空目录，处理器仍在
     * JVM classpath 上 → 照常运行，call()==true、零诊断）。因此"隔离 classpath 后观察
     * 处理器是否被发现"这种断言在**进程内**恒为真，是假测试面——只能改用选项装配断言。
     */
    @Test
    public void testProcessorPathIsAssembledIntoOptions() {
        File processorDir = tempDir.resolve("pp-dir").toFile();
        JdkJavaCompiler compiler = new JdkJavaCompiler();

        List<String> withPath = compiler.buildFileOptions(new JavaCompileOptions()
                .setProcessorPath(List.of(processorDir)));
        int idx = withPath.indexOf("-processorpath");
        assertTrue(idx >= 0, "-processorpath 必须出现在选项中: " + withPath);
        assertEquals(processorDir.getPath(), withPath.get(idx + 1));

        List<String> withoutPath = compiler.buildFileOptions(new JavaCompileOptions());
        assertFalse(withoutPath.contains("-processorpath"),
                "未设置处理器路径时不得出现该开关: " + withoutPath);
    }

    @Test
    public void testModulePathIsAssembledIntoOptions() {
        File moduleDir = tempDir.resolve("mp-dir").toFile();
        JdkJavaCompiler compiler = new JdkJavaCompiler();

        List<String> withPath = compiler.buildFileOptions(new JavaCompileOptions()
                .setModulePath(List.of(moduleDir)));
        int idx = withPath.indexOf("--module-path");
        assertTrue(idx >= 0, "--module-path 必须出现在选项中: " + withPath);
        assertEquals(moduleDir.getPath(), withPath.get(idx + 1));

        assertFalse(compiler.buildFileOptions(new JavaCompileOptions()).contains("--module-path"));
    }

    @Test
    public void testMissingGeneratedSourceDirIsAutoCreated() throws Exception {
        File main = writeSource("proc3b/Main.java", "package proc3b; public class Main { public int f() { return 1; } }");
        File genDir = tempDir.resolve("auto-gen-dir").toFile();

        // 与 -d 同向：不存在的生成源目录自动创建（javac 对 -d 也是自动建）
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-gensrc-auto"))
                        .setGeneratedSourceDir(genDir));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(genDir.isDirectory());
    }

    @Test
    public void testInvalidProcModeRejectedBeforeJavac() throws Exception {
        File main = writeSource("proc4/Main.java", "package proc4; public class Main { public int f() { return 1; } }");

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-proc-bad"))
                        .setProcMode("bogus"));

        assertFalse(result.isSuccess());
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals("nop.err.javac.invalid-option-value", error.getErrorCode());
        assertTrue(error.getMessage().contains("-proc"), error.getMessage());
    }

    @Test
    public void testMissingProcessorClassFailsWithDiagnostic() throws Exception {
        File main = writeSource("proc5/Main.java", "package proc5; public class Main { public int f() { return 1; } }");

        // 实测 javac 对 -proc:full -processor no.Such.Processor：call()==false、collector 1 条 ERROR
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-proc-missing"))
                        .setProcMode("full")
                        .setProcessors(List.of("no.Such.Processor")));

        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
        assertEquals(JavaDiagnostic.SOURCE_JAVAC, result.getErrors().get(0).getSource());
    }

    // ---------- 模块路径 ----------

    @Test
    public void testMissingModulePathRejectedByModuleNotJavac() throws Exception {
        File main = writeSource("mp/Main.java", "package mp; public class Main { public int f() { return 1; } }");

        // 实测 javac 对不存在的 --module-path：call()==true、零诊断、产物照旧
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-mp"))
                        .setModulePath(List.of(tempDir.resolve("no-such-module-dir").toFile())));

        assertFalse(result.isSuccess(), "不存在的模块路径必须被显式拒绝");
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals("nop.err.javac.invalid-option-value", error.getErrorCode());
        assertTrue(error.getMessage().contains("--module-path"), error.getMessage());
    }

    @Test
    public void testMissingClassPathEntryRejectedByModuleNotJavac() throws Exception {
        File main = writeSource("cp/Main.java", "package cp; public class Main { public int f() { return 1; } }");

        // 实测 javac 对不存在的 classpath 条目：call()==true、零诊断、产物照旧
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions()
                        .setOutputDir(out("out-cp"))
                        .setClassPaths(List.of(tempDir.resolve("no-such.jar").toString())));

        assertFalse(result.isSuccess(), "不存在的 classpath 条目必须被显式拒绝");
        assertEquals("nop.err.javac.invalid-option-value", result.getErrors().get(0).getErrorCode());
    }

    // ---------- 自定义参数透传 ----------

    @Test
    public void testExtraOptionsArePassedThrough() throws Exception {
        File main = writeSource("extra/Main.java", "package extra; public class Main { public int f() { return 1; } }");
        File outDir = out("out-extra");

        // -g:lines 是合法 javac 开关，用于验证 extraOptions 真的进了 javac 调用
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(main),
                new JavaCompileOptions().setOutputDir(outDir).setExtraOptions(List.of("-g:lines")));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(new File(outDir, "extra/Main.class").isFile());
    }

    @Test
    public void testUnknownExtraOptionMapsToNopException() throws Exception {
        File main = writeSource("extra2/Main.java", "package extra2; public class Main { public int f() { return 1; } }");

        // 实测 javac 对未知选项在 getTask 阶段抛 IllegalArgumentException
        io.nop.api.core.exceptions.NopException e = org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> new JdkJavaCompiler().compileFiles(List.of(main),
                        new JavaCompileOptions()
                                .setOutputDir(out("out-extra-bad"))
                                .setExtraOptions(List.of("-totallyBogusOption"))));
        assertEquals("nop.err.javac.compile-fail", e.getErrorCode());
    }

    // ---------- 测试处理器 ----------

    private static File testClassesDir() {
        // 处理器产物在本模块 target/test-classes；取 classpath 首项即该目录
        return new File(System.getProperty("java.class.path").split(File.pathSeparator)[0]);
    }

    private static String listDir(File dir) {
        String[] names = dir.list();
        return names == null ? "<none>" : String.join(",", names);
    }

    /**
     * 测试处理器。三个约束都是实测踩出来的：
     * <ul>
     *   <li>继承 AbstractProcessor 而非直接 implements Processor——JDK 26 上
     *       Processor 已有抽象的 getCompletions，直接实现无法跨 JDK 21/26 编译</li>
     *   <li>必须显式覆写 init 并调父类实现——否则 processingEnv 为 null，getFiler() NPE</li>
     *   <li>必须用一次性 boolean 闩锁做 round 守卫——只判 processingOver() 不足以防重复
     *       createSourceFile，最终注解轮会二次创建并抛 FilerException，被处理器报为 ERROR，
     *       造成假编译失败</li>
     * </ul>
     */
    public static class MarkingProcessor extends AbstractProcessor {
        private boolean generated;

        @Override
        public void init(ProcessingEnvironment processingEnv) {
            super.init(processingEnv);
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return new HashSet<>(Set.of("*"));
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (generated || roundEnv.processingOver())
                return false;

            try {
                JavaFileObject generatedSource = processingEnv.getFiler().createSourceFile("gen.Generated");
                try (var writer = generatedSource.openWriter()) {
                    writer.write("package gen; public class Generated { public static int tag() { return 1; } }");
                }
                generated = true;
            } catch (IOException e) {
                throw new IllegalStateException("processor-fail", e);
            }
            return false;
        }
    }
}