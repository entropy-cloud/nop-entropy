/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.env.PlatformEnv;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.nop.javac.JavaCompilerErrors.ARG_CLASS_NAME;
import static io.nop.javac.JavaCompilerErrors.ARG_DETAIL;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_COMPILE_FAIL;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_COMPILER_NOT_ACQUIRED;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_INVALID_CLASS_NAME;

public class JdkJavaCompiler {
    static final Logger LOG = LoggerFactory.getLogger(JdkJavaCompiler.class);

    /**
     * 进程内内存编译（GenAopProxy 经 exec:java 调用）的默认 classpath：遍历 TCCL 父链收集
     * URLClassLoader 的 URL（plexus ClassRealm 是其子类），转解码后的文件路径并去重。
     *
     * <p>为什么不能只读 java.class.path：exec:java 在 Maven JVM 内运行时该属性仅含
     * maven-wrapper.jar 一个文件（plan 2450 实测），用它做 -classpath 会使代理编译报
     * "找不到符号"——chronic gen-aop-proxy-fail 家族的根因。surefire/IDE 场景 TCCL 为
     * AppClassLoader（JDK 9+ 非 URLClassLoader 子类），收集结果为空，回退
     * java.class.path（即完整测试 classpath），行为与旧实现一致。
     *
     * <p>历史：b8157644a6（plan 2349）曾删除同类分支，其"实测恒走回退"仅在 surefire
     * 上下文成立；plan 2450 推翻该裁定。与旧实现的差别：本实现走 TCCL 全父链（含
     * target/classes 等 output URL，旧实现只查定义类加载器），并返回解码后的文件路径
     * 而非原始 url 字符串。
     */
    public static List<String> getDefaultClassPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        while (loader != null) {
            if (loader instanceof URLClassLoader urlLoader) {
                for (URL url : urlLoader.getURLs()) {
                    try {
                        File file = new File(url.toURI());
                        if (file.exists())
                            paths.add(file.getAbsolutePath());
                    } catch (Exception e) {
                        LOG.warn("nop.javac.classpath-url-skip:url={}", url, e);
                    }
                }
            }
            loader = loader.getParent();
        }

        // 回退/兜底：TCCL 链上无可收集 URL 时保持旧行为（surefire 等场景）；
        // 非空时拼接无妨（exec:java 下混入 wrapper.jar 无害）
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.isEmpty())
            paths.addAll(StringHelper.split(classPath, File.pathSeparatorChar));

        return new ArrayList<>(paths);
    }

    public JavaCompileResult compile(String className, String code, List<String> classPaths) {
        return compile(Collections.singletonList(new JavaSourceCode(className, code)), classPaths);
    }

    public JavaCompileResult compile(List<JavaSourceCode> sources, List<String> classPaths) {

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        final List<JavaFileObject> files = new ArrayList<>();
        for (JavaSourceCode sourceCode : sources) {
            String className = sourceCode.getClassName();
            String text = sourceCode.getCode();
            URI uri;
            try {
                uri = URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension);
            } catch (Exception e) {
                // className 含 URI 非法字符时立即以明确异常暴露，避免错误推迟到 javac
                // 内部以更难定位的形式爆发
                throw new NopException(ERR_JAVAC_INVALID_CLASS_NAME, e).param(ARG_CLASS_NAME, className);
            }

            final SimpleJavaFileObject sjfo = new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
                    return text;
                }
            };
            files.add(sjfo);
        }

        final List<String> options = buildOptions(classPaths);

        LOG.debug("java.compile_options:{}", options);

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        ClassLoaderImpl resultClassLoader = new ClassLoaderImpl(JdkJavaCompiler.class.getClassLoader());

        JavaFileManager fileManager = createFileManager(compiler, resultClassLoader);

        final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, files);

        // D9 裁定：Throwable 包装只施加于**新增的**文件式入口，不施加于本方法。
        // 理由：本路径的选项全部硬编码且合法（-source/-target 取运行 JDK 版本、
        // -classpath 由调用方给字符串），getTask 与 call 的失败分支在现有选项面下
        // 不可达；而施加包装会改变 GenAopProxy 的错误契约（它当前依赖未包装异常
        // 经 NopException.adapt 归一），构成对 AOP 生产路径的 live 行为变更。
        // 该残余已在 ai-dev/design/nop-javac/README.md §3.3 记录并登记 watch-only successor。
        JavaCompileResult result;
        try {
            result = new JavaCompileResult(task.call(), diagnostics, resultClassLoader);
        } finally {
            // fileManager 由本方法创建，javac 经它打开 classpath 上的 JAR，用完必须关闭，否则句柄泄漏
            // （Windows 上会锁定依赖 JAR）。ClassLoaderImpl 只持有内存字节流，不受关闭影响
            try {
                fileManager.close();
            } catch (IOException e) {
                LOG.warn("nop.javac.close-file-manager-fail", e);
            }
        }
        return result;
    }

    // ---- 编译器复用（可选，默认关闭；见 compileFiles 三参重载的契约说明）----
    private JavaCompiler sharedCompiler;
    private boolean acquired;
    private int createdCompilerCount;
    private JavaFileManager lastTaskFileManager;

    /**
     * acquire：取可复用的编译器实例。实测 getSystemJavaCompiler() 每次返回不同实例，
     * 因此复用必须显式持有字段。
     */
    synchronized JavaCompiler acquireCompiler() {
        if (sharedCompiler == null) {
            sharedCompiler = ToolProvider.getSystemJavaCompiler();
            createdCompilerCount++;
        }
        acquired = true;
        return sharedCompiler;
    }

    int getCreatedCompilerCount() {
        return createdCompilerCount;
    }

    /**
     * 上一轮真正交给 {@code getTask} 的 file manager。仅供同包测试断言"每轮确实是不同的
     * manager 实例"——断言计数或测试替身自己的 delegate 都无法排除"跨轮缓存同一个 manager
     * 并交给 javac"这一实现。
     */
    JavaFileManager getLastTaskFileManager() {
        return lastTaskFileManager;
    }

    /**
     * release：释放持有的编译器实例并回到"未 acquire"状态。
     *
     * <p>file manager 的关闭责任**不在**这里——它每轮由 compileFiles 在 finally 中关闭，
     * 因为 file manager 不复用（复用会跨轮污染 javac 上下文）。
     */
    synchronized void releaseCompiler() {
        if (!acquired)
            throw new NopException(ERR_JAVAC_COMPILER_NOT_ACQUIRED);

        sharedCompiler = null;
        acquired = false;
        createdCompilerCount = 0;
    }

    boolean isAcquired() {
        return acquired;
    }

    /**
     * 文件式编译入口：磁盘源文件 → 磁盘输出目录 → 结构化诊断。
     *
     * <p>与内存编译入口并存而非替代它：产物语义不同（磁盘目录 vs 内存字节表），
     * 既有 AOP 生产路径（GenAopProxy）继续走内存入口，行为不变。
     *
     * <p>错误契约（D9）：{@code getTask} / {@code call} / file manager 关闭三处的任何
     * {@code Throwable} 一律映射为带错误码的 NopException，不得有未包装异常逃逸。
     * 捕获范围必须覆盖 {@code Throwable}——实测 javac 内部会抛 {@code java.lang.Error}。
     */
    public JavaFileCompileResult compileFiles(List<File> sourceFiles, JavaCompileOptions options) {
        // 默认态是"每次新建编译器"——等价于本类引入复用能力之前的行为。
        // 复用会改变 GenAopProxy 生产路径的行为，与本计划的兼容性约束冲突。
        return compileFiles(sourceFiles, options, false);
    }

    /**
     * 带编译器复用开关的文件式编译。
     *
     * <p>复用契约（D8）：
     * <ul>
     *   <li>复用**只针对编译器实例**。实测 {@code ToolProvider.getSystemJavaCompiler()} 每次返回
     *       不同实例，因此复用必须显式持有字段，不能依赖调用方每次新建。</li>
     *   <li>file manager **每轮新建并在 finally 关闭**，即使复用了编译器。复用 file manager 会让
     *       javac 内部上下文跨轮污染。</li>
     *   <li>未 acquire 就 release、重复 release 一律抛带错误码异常，不静默忽略。</li>
     * </ul>
     */
    public JavaFileCompileResult compileFiles(List<File> sourceFiles, JavaCompileOptions options, boolean reuseCompiler) {
        JavaCompileOptions opts = options != null ? options : new JavaCompileOptions();

        JavaCompiler compiler;
        if (reuseCompiler) {
            compiler = acquireCompiler();
        } else {
            compiler = ToolProvider.getSystemJavaCompiler();
        }

        JavaDiagnosticCollector collector = new JavaDiagnosticCollector();

        // ---- 前置自校验：javac 对以下失败全部静默（call() 返回 true、collector 零诊断），
        // ---- 事后无法从 javac 反馈恢复，必须在 getTask 之前拦截
        JavaCompileValidator.validateOutputDir(opts.getOutputDir(), collector);
        JavaCompileValidator.validateSourceFilesExist(sourceFiles, collector);
        java.nio.charset.Charset charset = JavaCompileValidator.validateEncoding(opts.getEncoding(), collector);
        JavaCompileValidator.validateDecodable(sourceFiles, charset, collector);
        JavaCompileValidator.validateClassPaths(opts.getClassPaths(), collector);
        JavaCompileValidator.validatePathsExist(opts.getSourcePath(), "-sourcepath", collector);
        JavaCompileValidator.validatePathsExist(opts.getModulePath(), "--module-path", collector);
        JavaCompileValidator.validatePathsExist(opts.getProcessorPath(), "-processorpath", collector);
        JavaCompileValidator.validateGeneratedSourceDir(opts.getGeneratedSourceDir(), collector);
        JavaCompileValidator.validateImplicitPolicy(opts.getImplicitPolicy(), collector);
        JavaCompileValidator.validateProcMode(opts.getProcMode(), collector);

        if (collector.hasError()) {
            // 显式失败：返回带结构化诊断的失败结果，不产出任何 class
            LOG.warn("nop.javac.file-compile-precheck-fail:diagnostics={}", collector.getDiagnostics());
            return new JavaFileCompileResult(false, opts.getOutputDir(), collector.getDiagnostics());
        }

        JavaFileManager fileManager = null;
        StandardJavaFileManager inputFileManager = null;
        try {
            fileManager = createStandardFileManager(compiler);

            List<JavaFileObject> files = new ArrayList<>();
            if (sourceFiles != null) {
                // 源文件对象必须由**传给 getTask 的那个** file manager 产出：javac 在
                // task 的 context 里解析 -encoding，而 PathFileObject 的解码走创建它的 manager
                // 的 charset 通道。用独立的输入 manager 会让 -encoding 静默失效
                // （实测：源文件按 UTF-8 解码、含不可映射字符时产出替换字符）。
                // 缝被替换为非 StandardJavaFileManager 时才需要独立的输入 manager。
                if (fileManager instanceof StandardJavaFileManager standard) {
                    for (File file : sourceFiles) {
                        if (file == null)
                            continue;
                        for (JavaFileObject jfo : standard.getJavaFileObjects(file))
                            files.add(jfo);
                    }
                } else {
                    inputFileManager = compiler.getStandardFileManager(null, null, null);
                    for (File file : sourceFiles) {
                        if (file == null)
                            continue;
                        for (JavaFileObject jfo : inputFileManager.getJavaFileObjects(file))
                            files.add(jfo);
                    }
                }
            }

            List<String> options2 = buildFileOptions(opts);
            LOG.debug("java.compile_file_options:{}", options2);

            final DiagnosticCollector<JavaFileObject> javacDiagnostics = new DiagnosticCollector<>();

            final JavaCompiler.CompilationTask task = compiler.getTask(
                    new java.io.StringWriter(), fileManager, javacDiagnostics, options2, null, files);

            Boolean taskResult;
            try {
                taskResult = task.call();
            } catch (Throwable e) {
                throw wrapCompileError(e);
            }

            for (Diagnostic<? extends JavaFileObject> diagnostic : javacDiagnostics.getDiagnostics()) {
                collector.addJavacDiagnostic(diagnostic);
            }

            boolean success = taskResult != null && taskResult && !collector.hasError();
            lastTaskFileManager = fileManager;
            return new JavaFileCompileResult(success, opts.getOutputDir(), collector.getDiagnostics());
        } catch (NopException e) {
            throw e;
        } catch (Throwable e) {
            throw wrapCompileError(e);
        } finally {
            // 两个 manager 都要关闭：fileManager 是可被测试替换的缝，
            // inputFileManager 持有磁盘源文件句柄
            closeQuietly(fileManager);
            closeQuietly(inputFileManager);
        }
    }

    private static void closeQuietly(JavaFileManager fileManager) {
        if (fileManager == null)
            return;
        try {
            fileManager.close();
        } catch (IOException e) {
            LOG.warn("nop.javac.close-file-manager-fail", e);
        }
    }

    /**
     * 文件式编译的选项装配。charset 固定为 null —— 实测只有 file manager 的 charset 显式非 null
     * 时才会压制 CLI -encoding，两条通道同时设置会静默取其一且无任何测试能发现，故此处只走 CLI 通道。
     */
    List<String> buildFileOptions(JavaCompileOptions opts) {
        List<String> options = new ArrayList<>();

        if (opts.getRelease() != null && !opts.getRelease().isEmpty()) {
            options.add("--release");
            options.add(opts.getRelease());
        }

        options.add("-classpath");
        options.add(joinClassPaths(opts.effectiveClassPaths()));

        if (opts.getOutputDir() != null) {
            options.add("-d");
            options.add(opts.getOutputDir().getPath());
        }

        if (opts.getEncoding() != null && !opts.getEncoding().isEmpty()) {
            options.add("-encoding");
            options.add(opts.getEncoding());
        }

        if (opts.getSourcePath() != null && !opts.getSourcePath().isEmpty()) {
            options.add("-sourcepath");
            options.add(joinPaths(opts.getSourcePath()));
        }

        // -implicit: 与 -proc: 是单 token 开关：实测拆成两个 token 会被 javac
        // 判为"无效的标记"并在 getTask 阶段抛 IllegalArgumentException
        if (opts.getImplicitPolicy() != null)
            options.add("-implicit:" + opts.getImplicitPolicy());

        if (opts.getProcMode() != null)
            options.add("-proc:" + opts.getProcMode());

        if (opts.getProcessors() != null && !opts.getProcessors().isEmpty()) {
            options.add("-processor");
            options.add(StringHelper.join(opts.getProcessors(), ","));
        }

        if (opts.getProcessorPath() != null && !opts.getProcessorPath().isEmpty()) {
            options.add("-processorpath");
            options.add(joinPaths(opts.getProcessorPath()));
        }

        if (opts.getGeneratedSourceDir() != null) {
            options.add("-s");
            options.add(opts.getGeneratedSourceDir().getPath());
        }

        if (opts.getModulePath() != null && !opts.getModulePath().isEmpty()) {
            options.add("--module-path");
            options.add(joinPaths(opts.getModulePath()));
        }

        if (opts.getExtraOptions() != null)
            options.addAll(opts.getExtraOptions());

        return options;
    }

    /**
     * 标准 file manager 工厂。文件式编译路径**不包装** file manager —— 产物需由 javac
     * 按 -d 直接写出，包装会改变输出语义。
     *
     * <p>返回 {@link JavaFileManager} 而非具体类型，使同包测试可用
     * {@code ForwardingJavaFileManager} 包装并统计 {@code close()} 次数——句柄释放断言需要一个
     * 可注入计数的替换点。磁盘源文件的 file object 由独立的输入 manager 产出，故输入侧不受此缝影响。
     */
    protected JavaFileManager createStandardFileManager(JavaCompiler compiler) throws IOException {
        return compiler.getStandardFileManager(null, null, null);
    }

    private String joinClassPaths(List<String> classPaths) {
        List<String> urls = new ArrayList<>(classPaths.size());
        // windows不允许classpath为url格式
        for (String str : classPaths) {
            if (str.startsWith("file:/") && PlatformEnv.isWindows()) {
                urls.add(str.substring("file:/".length()));
            } else {
                urls.add(str);
            }
        }
        return StringHelper.join(urls, String.valueOf(File.pathSeparatorChar));
    }

    private String joinPaths(List<File> paths) {
        return StringHelper.join(paths.stream().map(File::getPath).toList(), File.pathSeparator);
    }

    private static NopException wrapCompileError(Throwable e) {
        return new NopException(ERR_JAVAC_COMPILE_FAIL, e).param(ARG_DETAIL, String.valueOf(e.getMessage()));
    }

    /**
     * javac 选项装配缝。选项原先是 compile() 内的局部变量，唯一出口是 LOG.debug，
     * 无任何可断言面；后续 Phase 的全部"开关生效/非法取值"测试都以本方法的返回值为断言对象。
     */
    List<String> buildOptions(List<String> classPaths) {
        List<String> options = new ArrayList<>();
        // 与运行 JDK 对齐：JDK 21 起 javac 对 source/target 8 仅弃用支持，未来版本将移除；
        // 固定 1.8 也会禁止生成代码使用新语法（如 var/record）
        String sourceLevel = String.valueOf(Runtime.version().feature());
        options.add("-source");
        options.add(sourceLevel);
        options.add("-target");
        options.add(sourceLevel);
        options.add("-classpath");

        List<String> urls = new ArrayList<>(classPaths.size());

        // windows不允许classpath为url格式
        for (String str : classPaths) {
            if (str.startsWith("file:/") && PlatformEnv.isWindows()) {
                urls.add(str.substring("file:/".length()));
            } else {
                urls.add(str);
            }
        }
        options.add(StringHelper.join(urls, String.valueOf(File.pathSeparatorChar)));
        return options;
    }

    static class ClassLoaderImpl extends SecureClassLoader {
        final private Map<String, ByteArrayOutputStream> byteStreams = new HashMap<String, ByteArrayOutputStream>();

        public ClassLoaderImpl(ClassLoader parent) {
            super(parent);
        }

        public Set<String> getGeneratedClassNames() {
            return byteStreams.keySet();
        }

        public byte[] getGeneratedClassBytes(String className) {
            ByteArrayOutputStream os = byteStreams.get(className);
            if (os == null) return null;
            return os.toByteArray();
        }

        @Override
        protected Class<?> findClass(final String className) throws ClassNotFoundException {
            final ByteArrayOutputStream bos = byteStreams.get(className);
            if (bos == null) {
                // ClassLoader 契约要求找不到类时抛 ClassNotFoundException，返回 null 会让
                // loadClass 的调用方拿到 null 并以 NPE 形态崩溃，丢失"类不存在"语义
                throw new ClassNotFoundException(className);
            }

            final byte[] b = bos.toByteArray();
            return super.defineClass(className, b, 0, b.length);
        }

        public OutputStream openOutputStream(String className) {
            ByteArrayOutputStream bos = byteStreams.get(className);
            if (bos == null) {
                bos = new ByteArrayOutputStream();
                byteStreams.put(className, bos);
            }
            return bos;
        }
    }

    /**
     * file manager 工厂缝。原先是包级两参方法，测试既无法替换也无法观察 close() 时机，
     * 句柄释放断言没有注入点。改为 protected 供同包测试子类替换。
     */
    protected JavaFileManager createFileManager(JavaCompiler compiler, final ClassLoaderImpl classLoader) {
        StandardJavaFileManager standard = compiler.getStandardFileManager(null, null, null);

        JavaFileManager fileManager = new ForwardingJavaFileManager<JavaFileManager>(standard) {

            @Override
            public ClassLoader getClassLoader(final Location location) {
                return classLoader;
            }

            @Override
            public JavaFileObject getJavaFileForOutput(final Location location, final String className, final JavaFileObject.Kind kind, final FileObject sibling) throws IOException {

                return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + kind.extension), kind) {

                    @Override
                    public OutputStream openOutputStream() throws IOException {
                        return classLoader.openOutputStream(className);
                    }
                };
            }
        };
        return fileManager;
    }
}