/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.javac.jdk;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final public class JavaCompileResult {
    static final Logger LOG = LoggerFactory.getLogger(JavaCompileResult.class);
    private final boolean success;
    private final List<CompileResultMessage> messages = new ArrayList<>();
    private final JdkJavaCompiler.ClassLoaderImpl resultClassLoader;

    public JavaCompileResult(final Boolean success, final DiagnosticCollector<JavaFileObject> diagnostics,
                             JdkJavaCompiler.ClassLoaderImpl resultClassLoader) {

        this.success = success != null && success;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            messages.add(new CompileResultMessage(diagnostic));
        }
        this.resultClassLoader = resultClassLoader;
    }

    public boolean isSuccess() {
        return success;
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

            final JavaFileObject sourceFileObject = diagnostic.getSource();
            String sourceCodePreliminary = null;
            if (sourceFileObject instanceof SimpleJavaFileObject) {
                final SimpleJavaFileObject simpleSourceFileObject = (SimpleJavaFileObject) sourceFileObject;

                try {
                    final CharSequence charSequence = simpleSourceFileObject.getCharContent(false);
                    sourceCodePreliminary = charSequence.toString();
                } catch (IOException e) {
                    LOG.error("nop.java.compile-error", e);
                }
            }
            if (sourceCodePreliminary == null) {
                sourceCode = "[SOURCE CODE UNAVAILABLE]";
            } else {
                sourceCode = sourceCodePreliminary;
            }

            compilerInfo = diagnostic;

            typeOfProblem = diagnostic.getKind().name();
            typeOfProblem_forDebugging = "toString() = " + diagnostic.getKind().toString() + "; name() = "
                    + typeOfProblem;

            lineNumber = (int) compilerInfo.getLineNumber();
            columnNumber = (int) compilerInfo.getColumnNumber();

            final int sourceLen = sourceCode.length();
            textHighlightPos_lineStart = (int) Math.min(Math.max(0, diagnostic.getStartPosition()), sourceLen);
            textHighlightPos_problemStart = (int) Math.min(Math.max(0, diagnostic.getPosition()), sourceLen);
            textHighlightPos_problemEnd = (int) Math.min(Math.max(0, diagnostic.getEndPosition()), sourceLen);

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

            codeOfConcern = sourceCode.substring(textHighlightPos_problemStart, textHighlightPos_problemEnd);
            codeOfConcernLong = sourceCode.substring(textHighlightPos_lineStart, textHighlightPos_problemEnd);
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