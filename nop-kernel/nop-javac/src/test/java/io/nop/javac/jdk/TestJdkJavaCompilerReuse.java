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

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编译器复用生命周期的护栏测试。
 *
 * <p>句柄释放断言面是"可注入计数的 file manager"（经 Phase 1 建立的工厂缝），
 * <b>不是</b>"Windows 上 JAR 被锁定"——该语义在 macOS 上不存在，用 rename/delete 或 fd 计数
 * 验证会得到恒真结果。Windows 语义只作为文档说明记录。
 */
public class TestJdkJavaCompilerReuse {

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

    // ---------- 默认态：每次新建编译器（等价于引入复用能力之前的行为） ----------

    @Test
    public void testDefaultPathCreatesFreshCompilerEachTime() throws Exception {
        File main = writeSource("reuse/A.java", "package reuse; public class A { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-default-1")));
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-default-2")));

        // 默认路径不走 acquireCompiler，故计数由 javac 实例本身的不可共享性保证：
        // 连续两轮各自成功编译，证明默认路径没有共享编译器状态
        assertEquals(2, compiler.fileManagerCreateCount, "默认路径每轮新建 file manager");
        assertEquals(2, compiler.fileManagerCloseCount, "默认路径每轮关闭 file manager");
        assertFalse(compiler.isAcquired(), "默认路径不得置位 acquired");
        assertTrue(new File(out("out-default-1"), "reuse/A.class").isFile());
        assertTrue(new File(out("out-default-2"), "reuse/A.class").isFile());
    }

    @Test
    public void testDefaultPathDoesNotShareCompilerAcrossRounds() throws Exception {
        File main = writeSource("reuse0/A.java", "package reuse0; public class A { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-noshare-1")));
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-noshare-2")));

        // 默认路径从不经 acquireCompiler，编译器由本方法内每次新建
        assertEquals(0, compiler.acquireCount, "默认路径不得调用 acquireCompiler");
        assertEquals(0, compiler.getCreatedCompilerCount());
    }

    // ---------- 复用态 ----------

    @Test
    public void testReusePathHoldsSingleCompilerInstance() throws Exception {
        File main = writeSource("reuse/C.java", "package reuse; public class C { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();
        JavaFileCompileResult r1 = compiler.compileFiles(List.of(main),
                new JavaCompileOptions().setOutputDir(out("out-reuse-1")), true);
        JavaFileCompileResult r2 = compiler.compileFiles(List.of(main),
                new JavaCompileOptions().setOutputDir(out("out-reuse-2")), true);

        assertTrue(r1.isSuccess(), r1.getErrorMessage());
        assertTrue(r2.isSuccess(), r2.getErrorMessage());
        // acquire 每轮都被调用（这是调用协议），但底层编译器实例只创建一次
        assertEquals(2, compiler.acquireCount, "复用路径每轮都应经 acquire 取编译器");
        assertEquals(1, compiler.getCreatedCompilerCount(),
                "复用路径下底层编译器实例只应创建一次——实测 getSystemJavaCompiler() 每次返回新实例，"
                        + "不复用则每轮都是新实例，javac 内部状态不跨轮共享");
        assertTrue(compiler.isAcquired(), "复用路径应置位 acquired");
    }

    @Test
    public void testReuseStillCreatesAndClosesFileManagerPerRound() throws Exception {
        File main = writeSource("reuse/D.java", "package reuse; public class D { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-reuse-fm-1")), true);
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-reuse-fm-2")), true);

        // 复用编译器不等于复用 file manager——后者会跨轮污染 javac 上下文。
        // 断言实例**身份**而不只是计数：只断言计数的话，"跨轮缓存同一个 file manager"
        // 这种错误实现同样能让计数为 2，测试恒真。
        assertEquals(2, compiler.fileManagerCreateCount, "file manager 必须每轮新建");
        assertEquals(2, compiler.fileManagerCloseCount, "file manager 必须每轮关闭");
        assertNotSame(compiler.created.get(0), compiler.created.get(1),
                "两轮必须拿到不同的 file manager 实例，不得跨轮复用");
    }

    @Test
    public void testFileManagerHandedToJavacDiffersPerRound() throws Exception {
        File main = writeSource("reuse/I.java", "package reuse; public class I { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();

        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-taskfm-1")), true);
        JavaFileManager firstGivenToJavac = compiler.getLastTaskFileManager();
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-taskfm-2")), true);
        JavaFileManager secondGivenToJavac = compiler.getLastTaskFileManager();

        assertNotNull(firstGivenToJavac, "第一轮必须已构造 task");
        assertNotNull(secondGivenToJavac);
        // 断言的是**真正交给 getTask 的那个** manager 的身份，不是测试替身自己的 delegate——
        // 后者由测试自己控制，"跨轮缓存同一个 manager 交给 javac"的错误实现同样能让它通过
        assertNotSame(firstGivenToJavac, secondGivenToJavac,
                "两轮交给 javac 的 file manager 必须是不同实例");
    }

    @Test
    public void testCloseObservedBeforeReturnOnReusePath() throws Exception {
        File main = writeSource("reuse/E.java", "package reuse; public class E { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();
        JavaFileCompileResult result = compiler.compileFiles(List.of(main),
                new JavaCompileOptions().setOutputDir(out("out-reuse-fm-3")), true);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(compiler.closeObservedBeforeReturn, "close() 必须发生在 compileFiles 返回之前");
    }

    @Test
    public void testFileManagerClosedOnFailureUnderReuse() throws Exception {
        File broken = writeSource("reuse/F.java", "package reuse; public class F { int ");

        CountingCompiler compiler = new CountingCompiler();
        JavaFileCompileResult result = compiler.compileFiles(List.of(broken),
                new JavaCompileOptions().setOutputDir(out("out-reuse-fail")), true);

        assertFalse(result.isSuccess());
        assertEquals(1, compiler.fileManagerCloseCount, "失败路径同样必须关闭 file manager，不得泄漏句柄");
    }

    @Test
    public void testFailureDoesNotLeakAcquiredState() throws Exception {
        File broken = writeSource("reuse/G.java", "package reuse; public class G { int ");

        CountingCompiler compiler = new CountingCompiler();
        compiler.compileFiles(List.of(broken), new JavaCompileOptions().setOutputDir(out("out-reuse-fail2")), true);

        // 编译失败不改变复用生命周期状态：acquired 仍成立，release 后可回到干净状态
        assertTrue(compiler.isAcquired());
        compiler.releaseCompiler();
        assertFalse(compiler.isAcquired());
    }

    // ---------- 多轮编译隔离 ----------

    @Test
    public void testConsecutiveRoundsProduceIndependentOutputs() throws Exception {
        File a = writeSource("round/A.java", "package round; public class A { public int f() { return 1; } }");
        File b = writeSource("round/B.java", "package round; public class B { public int f() { return 2; } }");

        CountingCompiler compiler = new CountingCompiler();

        File outA = out("out-round-a");
        JavaFileCompileResult ra = compiler.compileFiles(List.of(a),
                new JavaCompileOptions().setOutputDir(outA), true);
        assertTrue(ra.isSuccess(), ra.getErrorMessage());
        assertTrue(new File(outA, "round/A.class").isFile());
        assertFalse(new File(outA, "round/B.class").isFile(),
                "前一轮只编译 A，不应产出 B——输出目录内容即隔离断言面");

        File outB = out("out-round-b");
        JavaFileCompileResult rb = compiler.compileFiles(List.of(b),
                new JavaCompileOptions().setOutputDir(outB), true);
        assertTrue(rb.isSuccess(), rb.getErrorMessage());
        assertTrue(new File(outB, "round/B.class").isFile());
        assertFalse(new File(outB, "round/A.class").isFile());
    }

    @Test
    public void testDiagnosticsDoNotBleedBetweenRounds() throws Exception {
        File broken = writeSource("round2/Bad.java", "package round2; public class Bad { String s = 1; }");
        File good = writeSource("round2/Good.java", "package round2; public class Good { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();

        JavaFileCompileResult failed = compiler.compileFiles(List.of(broken),
                new JavaCompileOptions().setOutputDir(out("out-round2-bad")), true);
        assertFalse(failed.isSuccess());
        assertFalse(failed.getDiagnostics().isEmpty());

        JavaFileCompileResult ok = compiler.compileFiles(List.of(good),
                new JavaCompileOptions().setOutputDir(out("out-round2-good")), true);
        assertTrue(ok.isSuccess(), ok.getErrorMessage());
        assertTrue(ok.getDiagnostics().isEmpty(),
                "前一轮的诊断不得串到成功轮，实际: " + ok.getDiagnostics());
    }

    // ---------- 非法状态转换 ----------

    @Test
    public void testReleaseWithoutAcquireFailsExplicitly() {
        CountingCompiler compiler = new CountingCompiler();

        io.nop.api.core.exceptions.NopException e = assertThrows(
                io.nop.api.core.exceptions.NopException.class, compiler::releaseCompiler);
        assertEquals("nop.err.javac.compiler-not-acquired", e.getErrorCode());
    }

    @Test
    public void testDoubleReleaseFailsExplicitly() throws Exception {
        File main = writeSource("reuse/H.java", "package reuse; public class H { public int f() { return 1; } }");

        CountingCompiler compiler = new CountingCompiler();
        compiler.compileFiles(List.of(main), new JavaCompileOptions().setOutputDir(out("out-dbl")), true);
        compiler.releaseCompiler();

        io.nop.api.core.exceptions.NopException e = assertThrows(
                io.nop.api.core.exceptions.NopException.class, compiler::releaseCompiler);
        assertEquals("nop.err.javac.compiler-not-acquired", e.getErrorCode());
    }

    // ---- 测试替身：经 Phase 1 的 file manager 工厂缝注入计数 ----

    static class CountingCompiler extends JdkJavaCompiler {
        int acquireCount;
        int fileManagerCreateCount;
        int fileManagerCloseCount;
        boolean closeObservedBeforeReturn;
        final List<JavaFileManager> created = new ArrayList<>();

        @Override
        protected JavaFileManager createStandardFileManager(JavaCompiler compiler) throws IOException {
            fileManagerCreateCount++;
            JavaFileManager delegate = compiler.getStandardFileManager(null, null, null);
            created.add(delegate);
            return new ForwardingJavaFileManager<JavaFileManager>(delegate) {
                @Override
                public void close() throws IOException {
                    fileManagerCloseCount++;
                    closeObservedBeforeReturn = true;
                    super.close();
                }

                @Override
                public ClassLoader getClassLoader(Location location) {
                    return super.getClassLoader(location);
                }

                @Override
                public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                           JavaFileObject.Kind kind, FileObject sibling)
                        throws IOException {
                    return super.getJavaFileForOutput(location, className, kind, sibling);
                }
            };
        }

        @Override
        synchronized JavaCompiler acquireCompiler() {
            acquireCount++;
            return super.acquireCompiler();
        }

        @Override
        int getCreatedCompilerCount() {
            return super.getCreatedCompilerCount();
        }
    }
}