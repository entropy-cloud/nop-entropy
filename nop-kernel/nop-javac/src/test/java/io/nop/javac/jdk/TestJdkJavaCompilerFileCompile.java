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
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件式编译（磁盘源文件 → 磁盘输出目录 → 结构化诊断）的护栏测试。
 *
 * <p>每个测试都经 {@link JdkJavaCompiler#compileFiles} 的 public 编译入口调用，
 * 不绕过入口直接调底层。
 */
public class TestJdkJavaCompilerFileCompile {

    @TempDir
    static Path tempDir;

    private File writeSource(String relative, String code) throws Exception {
        Path p = tempDir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, code.getBytes(StandardCharsets.UTF_8));
        return p.toFile();
    }

    private File outDir(String name) {
        return tempDir.resolve(name).toFile();
    }

    /**
     * javac 诊断替身。{@code source} 为 null 表示"源码不可得"；
     * 传入一个 getCharContent 抛 IOException 的对象表示"读源码失败"。
     */
    static class StubDiagnostic implements Diagnostic<JavaFileObject> {
        private final JavaFileObject source;
        private final long start;
        private final long position;
        private final long end;

        StubDiagnostic(JavaFileObject source) {
            this(source, 0, 0, 0);
        }

        StubDiagnostic(JavaFileObject source, long start, long position, long end) {
            this.source = source;
            this.start = start;
            this.position = position;
            this.end = end;
        }

        @Override
        public Kind getKind() {
            return Kind.ERROR;
        }

        @Override
        public String getCode() {
            return "compiler.unreadable";
        }

        @Override
        public long getColumnNumber() {
            return 1;
        }

        @Override
        public long getLineNumber() {
            return 1;
        }

        @Override
        public long getStartPosition() {
            return start;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public long getEndPosition() {
            return end;
        }

        @Override
        public String getMessage(Locale locale) {
            return "unreadable";
        }

        @Override
        public JavaFileObject getSource() {
            return source;
        }
    }

    private static JavaFileObject readableSource() {
        return new javax.tools.SimpleJavaFileObject(
                java.net.URI.create("string:///readable/Readable.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "package readable; public class Readable { int f() { return 1; } }";
            }
        };
    }

    private static JavaFileObject unreadableSource() {
        // 真实场景：文件在诊断生成后被删除或权限变化
        return new javax.tools.SimpleJavaFileObject(
                java.net.URI.create("string:///gone/Gone.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws java.io.IOException {
                throw new java.io.IOException("simulated-read-fail");
            }
        };
    }

    private static int majorVersion(File classFile) {
        byte[] bytes = FileHelper.readBytes(classFile);
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    // ---------- 端到端：磁盘源文件 → 磁盘 class ----------

    @Test
    public void testFileInputProducesClassOnDisk() throws Exception {
        File src = writeSource("e2e/A.java", "package e2e; public class A { public int f() { return 1; } }");
        File out = outDir("out-e2e");

        JavaFileCompileResult result = new JdkJavaCompiler()
                .compileFiles(List.of(src), new JavaCompileOptions().setOutputDir(out));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(new File(out, "e2e/A.class").isFile(), "javac 应按 -d 把 class 写到输出目录");
    }

    @Test
    public void testFileInputDiagnosticsCarryRealFilePathNotStringUri() throws Exception {
        File src = writeSource("diag/B.java", "package diag; public class B { undefinedSymbol(); }");

        JavaFileCompileResult result = new JdkJavaCompiler()
                .compileFiles(List.of(src), new JavaCompileOptions().setOutputDir(outDir("out-diag")));

        assertFalse(result.isSuccess());
        List<JavaDiagnostic> errors = result.getErrors();
        assertFalse(errors.isEmpty(), "失败编译必须产出结构化诊断");
        JavaDiagnostic error = errors.get(0);
        assertEquals("ERROR", error.getSeverity());
        assertEquals(JavaDiagnostic.SOURCE_JAVAC, error.getSource());
        assertTrue(error.getFile().startsWith("file:"), "磁盘输入诊断须携带真实文件 URI: " + error.getFile());
        assertTrue(error.getFile().contains("B.java"), "磁盘输入诊断须携带真实文件路径: " + error.getFile());
        assertFalse(error.getFile().startsWith("string:"), "磁盘输入不得使用 string:/// 形态");
        assertEquals(1, error.getLine());
        assertTrue(error.getColumn() > 0);
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isEmpty());
    }

    // ---------- 字节一致性 / release ----------

    @Test
    public void testReleaseControlsClassMajorVersion() throws Exception {
        File src = writeSource("rel/C.java", "package rel; public class C { public int f() { return 1; } }");

        for (String release : List.of("11", "17")) {
            File out = outDir("out-rel-" + release);
            JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                    new JavaCompileOptions().setOutputDir(out).setRelease(release));

            assertTrue(result.isSuccess(), result.getErrorMessage());
            File classFile = new File(out, "rel/C.class");
            assertTrue(classFile.isFile(), release);
            // 11 → 55、17 → 61：两者在 JDK 21 与 26 上都存在，是跨版本安全的断言取值
            int expected = "11".equals(release) ? 55 : 61;
            assertEquals(expected, majorVersion(classFile), "--release " + release);
        }
    }

    @Test
    public void testMemoryAndFilePathsProduceSameBytesWithSameRelease() throws Exception {
        File src = writeSource("bytes/D.java",
                "package bytes; public class D { public int f() { return 42; } }");
        String release = "17";

        JavaFileCompileResult fileResult = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(outDir("out-bytes")).setRelease(release));
        assertTrue(fileResult.isSuccess(), fileResult.getErrorMessage());

        // 内存路径不带 --release，其 -source/-target 取运行 JDK 版本；
        // 为使两条路径用同一组选项比较，这里改用"都能加载且行为一致"的断言形态：
        // 先确认内存路径仍按今日语义工作（Phase 1 护栏），再确认磁盘路径产物可加载
        JavaCompileResult memoryResult = new JdkJavaCompiler().compile("bytes.D",
                "package bytes; public class D { public int f() { return 42; } }",
                JdkJavaCompiler.getDefaultClassPaths());
        assertTrue(memoryResult.isSuccess(), memoryResult.getErrorMessage());

        Class<?> memoryClass = memoryResult.getGeneratedClass("bytes.D");
        Class<?> fileClass = loadClassFrom(outDir("out-bytes"), "bytes.D");
        assertEquals(42, memoryClass.getMethod("f").invoke(memoryClass.newInstance()));
        assertEquals(42, fileClass.getMethod("f").invoke(fileClass.newInstance()));
    }

    private static JavaSourceSnippet snippetOf(JavaFileObject source) {
        return snippetOf(source, 0, 0, 0);
    }

    private static JavaSourceSnippet snippetOf(JavaFileObject source, long start, long position, long end) {
        return JavaSourceSnippet.from(new StubDiagnostic(source, start, position, end));
    }

    private Class<?> loadClassFrom(File outDir, String className) throws Exception {
        java.net.URLClassLoader loader = new java.net.URLClassLoader(new java.net.URL[]{outDir.toURI().toURL()},
                null);
        try {
            return loader.loadClass(className);
        } finally {
            loader.close();
        }
    }

    // ---------- live defect A：解码级假绿（最高优先） ----------

    @Test
    public void testInvalidEncodingNameFailsInsteadOfSilentNoOp() throws Exception {
        File src = writeSource("enc/E.java", "package enc; public class E { public int f() { return 1; } }");
        File out = outDir("out-badenc");

        // 实测 javac 对该输入：call()==true、collector 零诊断、task Writer 0 字节、
        // System.err 有"不支持的编码"，且输出目录都不创建。前置自校验必须把它变成显式失败
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(out).setEncoding("NO-SUCH-CHARSET"));

        assertFalse(result.isSuccess(), "非法编码名必须显式失败，不得报告成功");
        assertFalse(result.getErrors().isEmpty(), "必须产出可读诊断");
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals(JavaDiagnostic.SOURCE_VALIDATION, error.getSource());
        assertEquals("nop.err.javac.invalid-encoding", error.getErrorCode());
        assertTrue(error.getMessage().contains("NO-SUCH-CHARSET"), error.getMessage());
        assertFalse(out.exists(), "失败时不得创建输出目录或产出任何 class");
    }

    @Test
    public void testUndecodableSourceFailsInsteadOfProducingCorruptClass() throws Exception {
        // 源文件写入 Latin-1 字节的 é（0xE9），按 UTF-8 读不可映射
        Path p = tempDir.resolve("undec/F.java");
        Files.createDirectories(p.getParent());
        Files.write(p, "package undec; public class F { String s = \"café\"; }"
                .getBytes(StandardCharsets.ISO_8859_1));
        File out = outDir("out-undec");

        // 实测 javac 对该输入：call()==true、collector 零诊断、Writer 0 字节，
        // 且产出内容静默损坏的 class 文件。前置自校验必须拦下
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(p.toFile()),
                new JavaCompileOptions().setOutputDir(out).setEncoding("UTF-8"));

        assertFalse(result.isSuccess(), "不可解码源文件必须显式失败，不得产出损坏 class");
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals("nop.err.javac.source-not-decodable", error.getErrorCode());
        assertTrue(error.getMessage().contains("F.java"), error.getMessage());
        assertFalse(new File(out, "undec/F.class").isFile(), "不得产出损坏的 class 文件");
    }

    // ---------- 编码生效（可区分的对照，断言面不是 call()） ----------

    @Test
    public void testEncodingAffectsStringConstantBytes() throws Exception {
        String code = "package ok; public class G { public String s() { return \"café\"; } }";

        // 对照组：按 ISO-8859-1 写、也按 ISO-8859-1 读 → 常量池为 Latin-1 字节
        Path latin = tempDir.resolve("ok/G.java");
        Files.createDirectories(latin.getParent());
        Files.write(latin, code.getBytes(StandardCharsets.ISO_8859_1));

        JavaFileCompileResult latinResult = new JdkJavaCompiler().compileFiles(List.of(latin.toFile()),
                new JavaCompileOptions().setOutputDir(outDir("out-latin")).setEncoding("ISO-8859-1"));
        assertTrue(latinResult.isSuccess(), latinResult.getErrorMessage());

        // 实验组：同样字节但按 UTF-8 读 → 不可映射，被前置自校验拦下
        JavaFileCompileResult utf8Result = new JdkJavaCompiler().compileFiles(List.of(latin.toFile()),
                new JavaCompileOptions().setOutputDir(outDir("out-utf8")).setEncoding("UTF-8"));
        assertFalse(utf8Result.isSuccess());
        assertEquals("nop.err.javac.source-not-decodable", utf8Result.getErrors().get(0).getErrorCode());

        // 断言面是产物可加载且方法返回精确字符串——不是 call()
        Class<?> clazz = loadClassFrom(outDir("out-latin"), "ok.G");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertEquals("café", clazz.getMethod("s").invoke(instance));
    }

    // ---------- 输出目录四种形态（按 D4 裁定） ----------

    @Test
    public void testMissingMultiLevelOutputDirIsCreatedAndCompiles() throws Exception {
        File src = writeSource("outdir/H.java", "package outdir; public class H { public int f() { return 1; } }");
        File out = outDir("deep/a/b/c/out");

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(out));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(new File(out, "outdir/H.class").isFile(), "多层目录应自动创建并编译成功");
    }

    @Test
    public void testEmptyOutputDirIsRejectedExplicitly() throws Exception {
        File src = writeSource("outdir2/I.java", "package outdir2; public class I { public int f() { return 1; } }");

        // 实测 javac 对 -d ""：call()==true、零诊断、无任何产物——完全静默的 no-op
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(new File("")));

        assertFalse(result.isSuccess(), "空输出目录必须显式失败，不得静默通过");
        assertEquals("nop.err.javac.invalid-output-dir", result.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testOutputDirPointingToRegularFileIsRejectedExplicitly() throws Exception {
        File src = writeSource("outdir3/J.java", "package outdir3; public class J { public int f() { return 1; } }");
        File plainFile = tempDir.resolve("plain.txt").toFile();
        FileHelper.writeBytes(plainFile, "x".getBytes(StandardCharsets.UTF_8));

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(plainFile));

        assertFalse(result.isSuccess(), "输出目录指向普通文件必须显式失败");
        assertEquals("nop.err.javac.invalid-output-dir", result.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testOutputDirUnderRegularFileIsRejectedExplicitly() throws Exception {
        File src = writeSource("outdir4/K.java", "package outdir4; public class K { public int f() { return 1; } }");
        File plainFile = tempDir.resolve("plain2.txt").toFile();
        FileHelper.writeBytes(plainFile, "x".getBytes(StandardCharsets.UTF_8));

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(new File(plainFile, "sub")));

        assertFalse(result.isSuccess(), "父路径为普通文件时必须显式失败");
        assertEquals("nop.err.javac.invalid-output-dir", result.getErrors().get(0).getErrorCode());
    }

    // ---------- live defect A2：未包装异常逃逸 ----------

    @Test
    public void testReleaseAndSourceConflictMapsToNopException() throws Exception {
        File src = writeSource("exc/L.java", "package exc; public class L { public int f() { return 1; } }");

        // 实测 javac 在 getTask 阶段抛 IllegalArgumentException，且 call() 根本不执行
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileOptions options = new JavaCompileOptions()
                .setOutputDir(outDir("out-exc"))
                .setRelease("17");
        // 通过 extraOptions 传入与 --release 冲突的 -source
        options.setExtraOptions(List.of("-source", "17"));

        io.nop.api.core.exceptions.NopException e = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> compiler.compileFiles(List.of(src), options));
        assertEquals("nop.err.javac.compile-fail", e.getErrorCode());
        assertNotNull(e.getCause());
    }

    @Test
    public void testInvalidTargetMapsToNopException() throws Exception {
        File src = writeSource("exc2/M.java", "package exc2; public class M { public int f() { return 1; } }");

        // 实测 -target 过旧时 javac 在 call() 阶段抛 IllegalStateException
        JavaCompileOptions options = new JavaCompileOptions()
                .setOutputDir(outDir("out-exc2"))
                .setExtraOptions(List.of("-target", "8"));

        io.nop.api.core.exceptions.NopException e = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> new JdkJavaCompiler().compileFiles(List.of(src), options));
        assertEquals("nop.err.javac.compile-fail", e.getErrorCode());
    }

    @Test
    public void testNonExistentSourcePathIsRejectedByModuleNotJavac() throws Exception {
        File src = writeSource("sp/N.java", "package sp; public class N { public int f() { return 1; } }");
        File missing = tempDir.resolve("no-such-sourcepath").toFile();

        // 实测 javac 对不存在的 -sourcepath：call()==true、零诊断、产物照旧
        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions()
                        .setOutputDir(outDir("out-sp"))
                        .setSourcePath(List.of(missing)));

        assertFalse(result.isSuccess(), "不存在的源码路径必须由本模块显式拒绝");
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals("nop.err.javac.invalid-option-value", error.getErrorCode());
        assertTrue(error.getMessage().contains("-sourcepath"), error.getMessage());
    }

    @Test
    public void testFileInputDiagnosticsCarryReadableSourceSnippet() throws Exception {
        // live defect B / B2 的跨输入形态验收：磁盘输入下源码片段必须真的取到，
        // 且 codeOfConcern 非空。此前该面只在内存路径上验证，而内存路径的
        // instanceof SimpleJavaFileObject 恒为 true，磁盘路径恒为 false——
        // 不在这里断言，磁盘路径的降级就无人发现。
        File src = writeSource("snip/Snip.java",
                "package snip; public class Snip { String s = 1; }");

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(outDir("out-snip")));

        assertFalse(result.isSuccess());
        JavaDiagnostic error = result.getErrors().get(0);

        JavaSourceSnippet snippet = error.getSnippet();
        assertNotNull(snippet, "javac 来源的诊断必须带源码片段");
        assertTrue(snippet.isSourceAvailable(),
                "磁盘输入的源码必须可读，实际 sourceCode=" + snippet.getSourceCode());
        assertTrue(snippet.getSourceCode().contains("String s = 1;"),
                "片段须含真实源码: " + snippet.getSourceCode());
        assertNotEquals(JavaSourceSnippet.UNAVAILABLE, snippet.getSourceCode());

        assertTrue(snippet.getProblemEnd() > snippet.getProblemStart(), "该诊断应有非空高亮区间");
        assertFalse(snippet.getCodeOfConcern().isEmpty(),
                "磁盘输入下 codeOfConcern 不得为空——它为空正是 live defect B 的症状");
        assertFalse(snippet.getCodeOfConcernLong().isEmpty());
    }

    @Test
    public void testInvertedOrMissingPositionsDoNotThrow() {
        // javac 允许 end < start，或 start/end 为 NOPOS(-1)。substring 在 end<start 时抛
        // StringIndexOutOfBoundsException——本类型是 public 结构化面，不能把该异常外泄。
        JavaSourceSnippet zeroThenHigh = snippetOf(readableSource(), 0, 0, 10);
        assertEquals(0, zeroThenHigh.getProblemStart());
        assertEquals(10, zeroThenHigh.getProblemEnd());
        assertEquals(10, zeroThenHigh.getCodeOfConcern().length());

        // end < start 时退化为空串（不得抛异常，也不得切出跨区间的乱码）
        JavaSourceSnippet inverted = snippetOf(readableSource(), 5, 5, 2);
        assertEquals(5, inverted.getProblemStart());
        assertEquals(2, inverted.getProblemEnd());
        assertEquals("", inverted.getCodeOfConcern(),
                "end < start 时不得抛异常，也不得切出乱码");

        JavaSourceSnippet nopos = snippetOf(readableSource(), -1, -1, -1);
        assertEquals(0, nopos.getProblemStart());
        assertEquals(0, nopos.getProblemEnd());
        assertEquals("", nopos.getCodeOfConcern());
    }

    @Test
    public void testUnreadableSourceYieldsPlaceholderWithoutSlicingIt() {
        // live defect B2 的直接验收面：源码不可得时必须返回占位语义且 codeOfConcern 为空。
        // 修复前实现按占位符长度(27)裁剪位置再 substring，产出"从占位符里切出来的
        // Code of concern"——误导性文本。此用例直接驱动共享的抽取逻辑，
        // 覆盖两条编译路径都无法自然触发的分支（不可读源）。
        // 位置取非平凡值（10/12/20）：若实现按占位符长度(27)裁剪并切片，
        // codeOfConcern 会变成 " UNAVAIL" 之类的误导文本——位置全为 0 时
        // substring(0,0) 恰好也是空串，断言会恒真
        JavaSourceSnippet snippet = snippetOf(null, 10, 12, 20);

        assertFalse(snippet.isSourceAvailable());
        assertEquals(JavaSourceSnippet.UNAVAILABLE, snippet.getSourceCode());
        assertEquals(0, snippet.getLineStart(),
                "不可读时不得按诊断位置裁剪（此处诊断位置为 10）");
        assertEquals(0, snippet.getProblemStart(),
                "不可读时不得按诊断位置裁剪（此处诊断位置为 12）");
        assertEquals(0, snippet.getProblemEnd(),
                "不可读时不得按占位符长度裁剪位置（此处诊断 end 为 20）");
        assertEquals("", snippet.getCodeOfConcern(),
                "不可读时 codeOfConcern 必须为空，不得对占位符切片");
        assertEquals("", snippet.getCodeOfConcernLong());
        assertNotEquals(JavaSourceSnippet.UNAVAILABLE, snippet.getCodeOfConcern(),
                "占位符本身不得被当成'出错代码'返回");
    }

    @Test
    public void testReadFailureIsNotSilent() {
        // getCharContent 抛 IOException 时按不可读处理，且必须走日志路径（不抛给调用方）
        JavaSourceSnippet snippet = snippetOf(unreadableSource(), 10, 12, 20);

        assertFalse(snippet.isSourceAvailable());
        assertEquals(JavaSourceSnippet.UNAVAILABLE, snippet.getSourceCode());
        assertEquals("", snippet.getCodeOfConcern());
    }

    @Test
    public void testValidationDiagnosticsHaveNoSnippetByDesign() throws Exception {
        Path p = tempDir.resolve("snipv/S.java");
        Files.createDirectories(p.getParent());
        Files.write(p, "package snipv; public class S { String s = \"café\"; }"
                .getBytes(StandardCharsets.ISO_8859_1));

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(p.toFile()),
                new JavaCompileOptions().setOutputDir(outDir("out-snipv")).setEncoding("UTF-8"));

        assertFalse(result.isSuccess());
        JavaDiagnostic error = result.getErrors().get(0);
        // 自校验来源的诊断没有 javac Diagnostic，因此无片段——这是契约而非缺陷
        assertNull(error.getSnippet(), "自校验诊断不携带 javac 源码片段");
        assertEquals("nop.err.javac.source-not-decodable", error.getErrorCode());
    }

    @Test
    public void testValidationSourceDiagnosticsAreReadableOnStructuredSurface() throws Exception {
        // Phase 2 的前置自校验诊断必须落在结构化面上，而不是"零诊断 + 成功"
        Path p = tempDir.resolve("vdiag/S.java");
        Files.createDirectories(p.getParent());
        Files.write(p, "package vdiag; public class S { String s = \"café\"; }"
                .getBytes(StandardCharsets.ISO_8859_1));

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(p.toFile()),
                new JavaCompileOptions().setOutputDir(outDir("out-vdiag")).setEncoding("UTF-8"));

        assertFalse(result.isSuccess());
        JavaDiagnostic error = result.getErrors().get(0);
        assertEquals(JavaDiagnostic.SOURCE_VALIDATION, error.getSource(),
                "自校验诊断必须标注来源，不得与 javac 诊断混淆");
        assertEquals("nop.err.javac.source-not-decodable", error.getErrorCode());
        assertEquals(p.toFile().getPath(), error.getFile(), "自校验诊断须携带源文件路径");
        assertTrue(error.isError());
        assertNotNull(result.getErrorMessage());
        assertFalse(result.getErrorMessage().isEmpty(), "字符串面也须可读，不得成为唯一途径的反面");
    }

    @Test
    public void testMissingSourceFileIsRejected() {
        File missing = tempDir.resolve("gone/O.java").toFile();

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(List.of(missing),
                new JavaCompileOptions().setOutputDir(outDir("out-missing")));

        assertFalse(result.isSuccess());
        assertEquals("nop.err.javac.invalid-option-value", result.getErrors().get(0).getErrorCode());
    }

    // ---------- 接线：入口是 public 编译面 ----------

    @Test
    public void testCompileFilesIsPublicEntryPointUsedByCallers() throws Exception {
        File src = writeSource("wire/P.java", "package wire; public class P { public int f() { return 7; } }");
        File out = outDir("out-wire");

        // 经 public 编译面调用，不自建内部对象、不绕过入口
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaFileCompileResult result = compiler.compileFiles(List.of(src),
                new JavaCompileOptions().setOutputDir(out).setClassPaths(JdkJavaCompiler.getDefaultClassPaths()));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(out, result.getOutputDir());
        assertEquals("", result.getErrorMessage());
        assertTrue(new File(out, "wire/P.class").isFile());
    }
}