/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import io.nop.api.core.exceptions.ErrorCode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



/**
 * 编译诊断的收集器。合并两个来源：javac 的 DiagnosticCollector 与本模块前置自校验。
 */
class JavaDiagnosticCollector {
    private final List<JavaDiagnostic> diagnostics = new ArrayList<>();

    void addDiagnostic(JavaDiagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    /**
     * 从 javac 诊断构造结构化诊断，同时抽出源码片段。
     *
     * <p>片段抽取是两条编译路径的**共享**逻辑：若只在内存路径上做，磁盘输入会再次退化为
     * 静默的占位符（这正是 live defect B）。
     */
    void addJavacDiagnostic(javax.tools.Diagnostic<? extends javax.tools.JavaFileObject> diagnostic) {
        javax.tools.JavaFileObject source = diagnostic.getSource();
        diagnostics.add(new JavaDiagnostic(
                diagnostic.getKind().name(),
                JavaDiagnostic.SOURCE_JAVAC,
                source == null ? null : source.toUri().toString(),
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getMessage(java.util.Locale.US),
                null,
                JavaSourceSnippet.from(diagnostic)));
    }

    void addAll(List<JavaDiagnostic> list) {
        diagnostics.addAll(list);
    }

    void addError(ErrorCode errorCode, String... args) {
        String message = errorCode.getDescription();
        for (int i = 0; i + 1 < args.length; i += 2) {
            message = message.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        diagnostics.add(new JavaDiagnostic("ERROR", JavaDiagnostic.SOURCE_VALIDATION,
                null, 0, 0, message, errorCode.getErrorCode()));
    }

    void addSourceError(ErrorCode errorCode, File file, String... args) {
        String message = errorCode.getDescription();
        for (int i = 0; i + 1 < args.length; i += 2) {
            message = message.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        diagnostics.add(new JavaDiagnostic("ERROR", JavaDiagnostic.SOURCE_VALIDATION,
                file.getPath(), 0, 0, message, errorCode.getErrorCode()));
    }

    List<JavaDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    boolean hasError() {
        return diagnostics.stream().anyMatch(JavaDiagnostic::isError);
    }
}