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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.javac.JavaCompilerErrors.ARG_CLASS_NAME;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_INVALID_CLASS_NAME;

public class JdkJavaCompiler {
    static final Logger LOG = LoggerFactory.getLogger(JdkJavaCompiler.class);

    public static List<String> getDefaultClassPaths() {
        ClassLoader classLoader = JdkJavaCompiler.class.getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) classLoader).getURLs();
            List<String> list = new ArrayList<>();
            for (URL url : urls) {
                list.add(url.toString());
            }
            return list;
        }
        String classPath = System.getProperty("java.class.path");
        return StringHelper.split(classPath, File.pathSeparatorChar);
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

        LOG.debug("java.compile_options:{}", options);

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        ClassLoaderImpl resultClassLoader = new ClassLoaderImpl(JdkJavaCompiler.class.getClassLoader());

        JavaFileManager fileManager = createFileManager(compiler, resultClassLoader);

        final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, files);

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

    List<File> urlToFiles(List<String> urls) {
        List<File> ret = new ArrayList<>();
        for (String url : urls) {
            File file = new File(url);
            ret.add(file);
        }
        return ret;
    }

    JavaFileManager createFileManager(JavaCompiler compiler, final ClassLoaderImpl classLoader) {
        StandardJavaFileManager standard = compiler.getStandardFileManager(null, null, null);
//        try {
//            standard.setLocation(StandardLocation.CLASS_PATH, urlToFiles(classPaths));
//        } catch (Exception e) {
//            throw NopException.adapt(e);
//        }

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