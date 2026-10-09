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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Locale;

/**
 * javac 诊断的源码片段与高亮区间。
 *
 * <p>与 {@link JavaDiagnostic} 分工：后者是**可序列化**的结构化面（服务消费），本类是
 * **源码细节**面（人读调试）。两者都被内存编译路径与文件式编译路径共用——共用是必须的，
 * 因为源码可读性判断一旦只存在于其中一条路径，另一条路径就会静默降级。
 */
@DataBean
public class JavaSourceSnippet {
    static final Logger LOG = LoggerFactory.getLogger(JavaSourceSnippet.class);

    /**
     * 源码不可得时的占位语义。调用方据此识别"这不是真实源码"。
     */
    public static final String UNAVAILABLE = "[SOURCE CODE UNAVAILABLE]";

    private final String sourceCode;
    private final boolean sourceAvailable;
    private final int lineStart;
    private final int problemStart;
    private final int problemEnd;
    private final String codeOfConcern;
    private final String codeOfConcernLong;

    private JavaSourceSnippet(String sourceCode, boolean sourceAvailable, int lineStart, int problemStart,
                              int problemEnd, String codeOfConcern, String codeOfConcernLong) {
        this.sourceCode = sourceCode;
        this.sourceAvailable = sourceAvailable;
        this.lineStart = lineStart;
        this.problemStart = problemStart;
        this.problemEnd = problemEnd;
        this.codeOfConcern = codeOfConcern;
        this.codeOfConcernLong = codeOfConcernLong;
    }

    @JsonProperty("sourceCode")
    public String getSourceCode() {
        return sourceCode;
    }

    @JsonProperty("sourceAvailable")
    public boolean isSourceAvailable() {
        return sourceAvailable;
    }

    @JsonProperty("lineStart")
    public int getLineStart() {
        return lineStart;
    }

    @JsonProperty("problemStart")
    public int getProblemStart() {
        return problemStart;
    }

    @JsonProperty("problemEnd")
    public int getProblemEnd() {
        return problemEnd;
    }

    /**
     * 出错处的精确片段。源码不可得时为**空串**——不得对占位符做切片。
     */
    @JsonProperty("codeOfConcern")
    public String getCodeOfConcern() {
        return codeOfConcern;
    }

    @JsonProperty("codeOfConcernLong")
    public String getCodeOfConcernLong() {
        return codeOfConcernLong;
    }

    public static JavaSourceSnippet from(Diagnostic<? extends JavaFileObject> diagnostic) {
        return from(diagnostic, Locale.US);
    }

    public static JavaSourceSnippet from(Diagnostic<? extends JavaFileObject> diagnostic, Locale locale) {
        // 按 JavaFileObject 抽象接口判断可读性。修复前判 instanceof SimpleJavaFileObject：
        // 磁盘输入返回的是 com.sun.tools.javac.file.PathFileObject$SimpleFileObject（JDK 9+ 从来如此），
        // 该判断恒为 false，源码片段静默降级为占位符且无任何日志。
        String source = readSourceCode(diagnostic);

        if (source == null) {
            LOG.warn("nop.javac.diagnostic-source-unavailable:file={}", diagnostic.getSource());
            return new JavaSourceSnippet(UNAVAILABLE, false, 0, 0, 0, "", "");
        }

        int sourceLen = source.length();
        int lineStart = clamp(diagnostic.getStartPosition(), sourceLen);
        int problemStart = clamp(diagnostic.getPosition(), sourceLen);
        int problemEnd = clamp(diagnostic.getEndPosition(), sourceLen);

        // 防御顺序：javac 允许 end < start 或 start > end（诊断位置缺失时 NOPOS = -1）。
        // substring 在 end < start 时会抛 StringIndexOutOfBoundsException，而本类型是
        // public 结构化面的一部分——不能把 javac 的位置异常外泄给调用方。
        int concernEnd = Math.max(problemStart, problemEnd);
        int concernLongStart = Math.min(lineStart, problemStart);
        if (problemEnd < problemStart)
            concernEnd = problemStart;   // 区间倒置：退化为空，不切出跨区间文本

        return new JavaSourceSnippet(source, true, lineStart, problemStart, problemEnd,
                source.substring(problemStart, concernEnd),
                source.substring(concernLongStart, concernEnd));
    }

    private static int clamp(long position, int sourceLen) {
        if (position < 0)
            return 0;
        return (int) Math.min(position, sourceLen);
    }

    private static String readSourceCode(Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject sourceFileObject = diagnostic.getSource();
        if (sourceFileObject == null)
            return null;

        try {
            CharSequence charSequence = sourceFileObject.getCharContent(false);
            return charSequence != null ? charSequence.toString() : null;
        } catch (IOException e) {
            LOG.warn("nop.javac.read-diagnostic-source-fail:file={}", sourceFileObject.getName(), e);
            return null;
        }
    }
}