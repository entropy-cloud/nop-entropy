/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.Test;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编译器的两个可断言接缝（选项装配缝 + file manager 工厂缝）的护栏测试。
 * 缝原先不可断言，本类通过真实 compile() 调用路径证明二者确实在路径上、且被真实调用。
 */
public class TestJdkJavaCompilerSeams {

    @Test
    public void testOptionAssemblySeamLocksCurrentBaseline() {
        List<String> options = new JdkJavaCompiler().buildOptions(List.of("/a/b.jar", "/c/d.jar"));

        String sourceLevel = String.valueOf(Runtime.version().feature());
        assertEquals("-source", options.get(0));
        assertEquals(sourceLevel, options.get(1));
        assertEquals("-target", options.get(2));
        assertEquals(sourceLevel, options.get(3));
        assertEquals("-classpath", options.get(4));
        assertEquals(String.join(File.pathSeparator, "/a/b.jar", "/c/d.jar"), options.get(5));
        assertEquals(6, options.size());
    }

    @Test
    public void testOptionAssemblySeamJoinsEmptyClasspath() {
        List<String> options = new JdkJavaCompiler().buildOptions(List.of());
        // 空 classpath 仍产出 "-classpath" + "" 两项，总数不变（-classpath 标志不因空值被省略）
        assertEquals(6, options.size());
        assertEquals("-classpath", options.get(4));
        assertEquals("", options.get(5));
    }

    @Test
    public void testOptionAssemblySeamIsUsedByRealCompilePath() {
        RecordingCompiler compiler = new RecordingCompiler();

        JavaCompileResult result = compiler.compile("demo._SeamA", "package demo; public class _SeamA {}",
                JdkJavaCompiler.getDefaultClassPaths());

        assertTrue(result.isSuccess());
        // 断言缝在真实编译路径上被调用：compile() 用的正是 buildOptions 的返回值
        assertEquals(new JdkJavaCompiler().buildOptions(JdkJavaCompiler.getDefaultClassPaths()),
                compiler.lastOptions);
    }

    @Test
    public void testFileManagerSeamIsUsedAndClosedExactlyOnce() {
        RecordingCompiler compiler = new RecordingCompiler();

        JavaCompileResult result = compiler.compile("demo._SeamB", "package demo; public class _SeamB {}",
                JdkJavaCompiler.getDefaultClassPaths());

        assertTrue(result.isSuccess());
        assertEquals(1, compiler.createCount, "compile() 必须经由 createFileManager 缝创建 file manager");
        assertEquals(1, compiler.closeCount, "compile() 必须在 finally 中关闭 file manager 恰好一次");
        assertTrue(compiler.closeObservedBeforeReturn, "close() 必须发生在 compile() 返回之前");
    }

    @Test
    public void testFileManagerSeamSurvivesFailedCompile() {
        RecordingCompiler compiler = new RecordingCompiler();

        JavaCompileResult result = compiler.compile("demo._SeamBroken", "package demo; public class _SeamBroken { int ",
                JdkJavaCompiler.getDefaultClassPaths());

        assertFalse(result.isSuccess());
        assertEquals(1, compiler.closeCount, "编译失败路径同样必须关闭 file manager，不得泄漏句柄");
    }

    static class RecordingCompiler extends JdkJavaCompiler {
        int createCount;
        int closeCount;
        boolean closeObservedBeforeReturn;
        List<String> lastOptions;

        @Override
        protected JavaFileManager createFileManager(javax.tools.JavaCompiler compiler,
                                                   JdkJavaCompiler.ClassLoaderImpl classLoader) {
            createCount++;
            JavaFileManager fm = super.createFileManager(compiler, classLoader);
            return new ForwardingJavaFileManager<JavaFileManager>(fm) {
                @Override
                public void close() {
                    closeCount++;
                    closeObservedBeforeReturn = true;
                    try {
                        fm.close();
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            };
        }

        @Override
        List<String> buildOptions(List<String> classPaths) {
            lastOptions = super.buildOptions(classPaths);
            return lastOptions;
        }
    }
}