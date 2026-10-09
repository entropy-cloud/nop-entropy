/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import io.nop.api.core.annotations.data.DataBean;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 结构化编译诊断。
 *
 * <p>诊断有两个来源，缺一不可：
 * <ul>
 *   <li>javac 的 DiagnosticCollector —— 语法/语义错误</li>
 *   <li>前置自校验（{@link JavaCompileValidator}）—— 编码与输出目录类失败。
 *       实测后者是唯一能观测到"call() 返回 true 但编译实际失败"那一类失败的来源，
 *       缺少它结构化面就会把失败报成"零诊断 + 成功"。</li>
 * </ul>
 */
@DataBean
public class JavaDiagnostic {
    /**
     * 诊断来源标识。JAVAC = 来自 javac 收集器；VALIDATION = 来自本模块前置自校验。
     */
    public static final String SOURCE_JAVAC = "javac";
    public static final String SOURCE_VALIDATION = "validation";

    private final String severity;
    private final String source;
    private final String file;
    private final long line;
    private final long column;
    private final String message;
    private final String errorCode;
    private final JavaSourceSnippet snippet;

    public JavaDiagnostic(@JsonProperty("severity") String severity,
                          @JsonProperty("source") String source,
                          @JsonProperty("file") String file,
                          @JsonProperty("line") long line,
                          @JsonProperty("column") long column,
                          @JsonProperty("message") String message,
                          @JsonProperty("errorCode") String errorCode) {
        this(severity, source, file, line, column, message, errorCode, null);
    }

    public JavaDiagnostic(@JsonProperty("severity") String severity,
                          @JsonProperty("source") String source,
                          @JsonProperty("file") String file,
                          @JsonProperty("line") long line,
                          @JsonProperty("column") long column,
                          @JsonProperty("message") String message,
                          @JsonProperty("errorCode") String errorCode,
                          @JsonProperty("snippet") JavaSourceSnippet snippet) {
        this.severity = severity;
        this.source = source;
        this.file = file;
        this.line = line;
        this.column = column;
        this.message = message;
        this.errorCode = errorCode;
        this.snippet = snippet;
    }

    /**
     * 源码片段与高亮区间。自校验来源的诊断没有对应 javac Diagnostic，为 null。
     *
     * <p>磁盘输入的诊断**有**非空 snippet——这正是"源码可读性判断必须两条路径共用"的验收面。
     */
    @JsonProperty("snippet")
    public JavaSourceSnippet getSnippet() {
        return snippet;
    }

    public String getSeverity() {
        return severity;
    }

    /**
     * {@link #SOURCE_JAVAC} 或 {@link #SOURCE_VALIDATION}。
     */
    public String getSource() {
        return source;
    }

    /**
     * 源文件标识。磁盘输入为真实文件路径；内存输入为 {@code string:///} URI 形态（既有行为，不改）。
     */
    public String getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public long getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 错误码。前置自校验来源必有；javac 来源为 null（javac 不产出本平台错误码）。
     */
    public String getErrorCode() {
        return errorCode;
    }

    public boolean isError() {
        return "ERROR".equals(severity);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity).append(' ').append(source);
        if (file != null)
            sb.append(" ").append(file);
        if (line > 0)
            sb.append(':').append(line);
        if (column > 0)
            sb.append(':').append(column);
        if (errorCode != null)
            sb.append(" [").append(errorCode).append(']');
        return sb.append(": ").append(message).toString();
    }
}