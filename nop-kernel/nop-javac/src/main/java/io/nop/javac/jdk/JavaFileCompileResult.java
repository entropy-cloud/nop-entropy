/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 文件式编译的结果。产物由 javac 直接写入磁盘输出目录，本类只承载成功判据与结构化诊断。
 *
 * <p>与 {@link JavaCompileResult} 分立而非扩展它：后者是 final 类且产物是内存字节表
 * （经包级私有的类加载器），无法表达磁盘目录产物；扩展它必然要求去掉 final 或改 public
 * 构造函数签名，而两者都是公共契约变更。
 */
public class JavaFileCompileResult {
    private final boolean success;
    private final File outputDir;
    private final List<JavaDiagnostic> diagnostics;

    JavaFileCompileResult(boolean success, File outputDir, List<JavaDiagnostic> diagnostics) {
        this.success = success;
        this.outputDir = outputDir;
        this.diagnostics = Collections.unmodifiableList(diagnostics);
    }

    public boolean isSuccess() {
        return success;
    }

    public File getOutputDir() {
        return outputDir;
    }

    /**
     * 结构化诊断，含 javac 诊断与前置自校验诊断两个来源。
     */
    public List<JavaDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    public List<JavaDiagnostic> getErrors() {
        return diagnostics.stream().filter(JavaDiagnostic::isError).toList();
    }

    /**
     * 与 {@link JavaCompileResult#getErrorMessage()} 同形的字符串面，保留给只需文本的调用方。
     */
    public String getErrorMessage() {
        if (success)
            return "";
        StringBuilder sb = new StringBuilder();
        for (JavaDiagnostic d : diagnostics) {
            if (sb.length() > 0)
                sb.append('\n');
            sb.append(d);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getErrorMessage();
    }
}