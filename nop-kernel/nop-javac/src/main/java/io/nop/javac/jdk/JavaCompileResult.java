/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final public class JavaCompileResult {
    static final Logger LOG = LoggerFactory.getLogger(JavaCompileResult.class);
    private final boolean success;
    private final List<CompileResultMessage> messages = new ArrayList<>();
    private final List<JavaDiagnostic> diagnostics = new ArrayList<>();
    private final JdkJavaCompiler.ClassLoaderImpl resultClassLoader;

    public JavaCompileResult(final Boolean success, final DiagnosticCollector<JavaFileObject> diagnostics,
                             JdkJavaCompiler.ClassLoaderImpl resultClassLoader) {

        this.success = success != null && success;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            messages.add(new CompileResultMessage(diagnostic));
            this.diagnostics.add(toJavaDiagnostic(diagnostic));
        }
        this.resultClassLoader = resultClassLoader;
    }

    private static JavaDiagnostic toJavaDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject source = diagnostic.getSource();
        return new JavaDiagnostic(
                diagnostic.getKind().name(),
                JavaDiagnostic.SOURCE_JAVAC,
                source == null ? null : source.toUri().toString(),
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getMessage(Locale.US),
                null,
                JavaSourceSnippet.from(diagnostic));
    }

    public boolean isSuccess() {
        return success;
    }

    /**
     * 结构化诊断访问面。诊断有两个来源：
     * <ul>
     *   <li>{@link JavaDiagnostic#SOURCE_JAVAC} — javac 收集器给出的语法/语义诊断</li>
     *   <li>{@link JavaDiagnostic#SOURCE_VALIDATION} — 本模块前置自校验产生的诊断。
     *       内存编译路径当前不产出后者（其选项面无可校验的编码/输出目录通道），
     *       文件式编译路径见 {@link JavaFileCompileResult#getDiagnostics()}</li>
     * </ul>
     *
     * <p>此前 messages 列表为 private 且无访问器，调用方只能经 {@link #toString()} 拿字符串，
     * 无法结构化消费。
     */
    public List<JavaDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public List<JavaDiagnostic> getErrors() {
        return diagnostics.stream().filter(JavaDiagnostic::isError).toList();
    }

    /**
     * 诊断消息列表，只读视图。保留 CompileResultMessage 形态（含源码片段与高亮区间），
     * 供需要 javac 原始诊断细节的调用方使用。
     */
    public List<CompileResultMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public Set<String> getGeneratedClassNames() {
        return resultClassLoader.getGeneratedClassNames();
    }

    public byte[] getGeneratedClassBytes(String className) {
        return resultClassLoader.getGeneratedClassBytes(className);
    }

    public Class<?> getGeneratedClass(String className) {
        try {
            return resultClassLoader.loadClass(className);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public void saveGenerated(File outputDir) {
        for (String className : getGeneratedClassNames()) {
            String fileName = className.replace('.', '/') + ".class";
            byte[] data = resultClassLoader.getGeneratedClassBytes(className);
            FileHelper.writeBytes(new File(outputDir, fileName), data);
        }
    }

    public String getErrorMessage() {
        if (this.isSuccess())
            return "";
        return this.toString();
    }

    public String toString() {
        return messages.toString();
    }

    /**
     * javac 原始诊断细节。只读用途，供需要源码片段与高亮区间的调用方。
     */
    final public static class CompileResultMessage {

        final public Diagnostic<? extends JavaFileObject> compilerInfo;

        final public String typeOfProblem;
        final public String typeOfProblem_forDebugging;

        final public String multiLineMessage;

        final public int lineNumber;
        final public int columnNumber;

        final public int textHighlightPos_lineStart;
        final public int textHighlightPos_problemStart;
        final public int textHighlightPos_problemEnd;

        final public String sourceCode;
        final public String codeOfConcern;
        final public String codeOfConcernLong;

        CompileResultMessage(final Diagnostic<? extends JavaFileObject> diagnostic) {

            // 源码可读性判断与高亮区间计算集中在 JavaSourceSnippet，与文件式编译路径共用。
            // 修复前本类独立判 instanceof SimpleJavaFileObject：磁盘输入返回的是
            // com.sun.tools.javac.file.PathFileObject$SimpleFileObject（JDK 9+ 从来如此），
            // 该判断恒为 false，源码片段静默降级为占位符且无任何日志。
            final JavaSourceSnippet snippet = JavaSourceSnippet.from(diagnostic, Locale.US);

            compilerInfo = diagnostic;

            typeOfProblem = diagnostic.getKind().name();
            typeOfProblem_forDebugging = "toString() = " + diagnostic.getKind().toString() + "; name() = "
                    + typeOfProblem;

            lineNumber = (int) compilerInfo.getLineNumber();
            columnNumber = (int) compilerInfo.getColumnNumber();

            sourceCode = snippet.getSourceCode();
            textHighlightPos_lineStart = snippet.getLineStart();
            textHighlightPos_problemStart = snippet.getProblemStart();
            textHighlightPos_problemEnd = snippet.getProblemEnd();

            final StringBuilder reformattedMessage = new StringBuilder();
            final String message = diagnostic.getMessage(Locale.US);
            final int messageCutOffPosition = message.indexOf("location:");
            final String[] messageParts;
            if (messageCutOffPosition >= 0) {
                messageParts = message.substring(0, messageCutOffPosition).split("\n");
            } else {
                messageParts = message.split("\n");
            }
            for (String s : messageParts) {
                String s2 = s.trim();
                if (!s2.isEmpty()) {
                    boolean lengthChanged;
                    do {
                        final int lBeforeReplace = s2.length();
                        s2 = s2.replace("  ", " ");
                        lengthChanged = (s2.length() != lBeforeReplace);
                    } while (lengthChanged);
                    reformattedMessage.append(s2).append("\n");
                }
            }

            // 源码不可得时置空，不得对占位符字符串切片——原实现会产出"从占位符里切出来的
            // Code of concern"，属误导性文本而非静默无值
            codeOfConcern = snippet.getCodeOfConcern();
            codeOfConcernLong = snippet.getCodeOfConcernLong();
            if (!codeOfConcern.isEmpty()) {
                reformattedMessage.append("Code of concern: \"").append(codeOfConcern).append('\"');
            }
            multiLineMessage = reformattedMessage.toString();
        }

        public String toStringForDebugging() {

            final StringBuilder ret = new StringBuilder();

            ret.append("Message:\n").append(multiLineMessage).append("\n");

            ret.append("line: ").append(lineNumber).append(",");
            ret.append("col: ").append(columnNumber).append("\n");

            return ret.toString();
        }

        @Override
        public String toString() {
            return toStringForDebugging();
        }
    }
}