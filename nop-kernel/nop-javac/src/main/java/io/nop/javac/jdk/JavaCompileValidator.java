/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.util.List;

import static io.nop.javac.JavaCompilerErrors.ARG_DETAIL;
import static io.nop.javac.JavaCompilerErrors.ARG_ENCODING;
import static io.nop.javac.JavaCompilerErrors.ARG_OPTION;
import static io.nop.javac.JavaCompilerErrors.ARG_PATH;
import static io.nop.javac.JavaCompilerErrors.ARG_SOURCE_FILE;
import static io.nop.javac.JavaCompilerErrors.ARG_VALUE;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_COMPILE_FAIL;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_INVALID_ENCODING;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_INVALID_OPTION_VALUE;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_INVALID_OUTPUT_DIR;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_SOURCE_NOT_DECODABLE;

/**
 * 编译入口的前置自校验。
 *
 * <p>存在的理由是实测（JDK 26.0.1）确认：javac 的解码级/选项级失败**完全绕开**
 * {@link javax.tools.DiagnosticCollector} 与 task {@code Writer}，只经进程级 System.err，
 * 而 {@code CompilationTask.call()} 仍返回 {@code true}。事后无法从 javac 反馈中恢复，
 * 因此必须在 {@code getTask} 之前拦截。
 *
 * <p>校验失败**不抛异常**，而是产出 {@link JavaDiagnostic} 交由调用方读取：
 * AI 编译服务的反馈契约是结构化诊断，抛异常会让这类失败脱离诊断面而重新变成静默。
 * 但失败一定是显式的——{@code isSuccess()} 为 false，且不产出任何 class 文件。
 */
class JavaCompileValidator {
    static final Logger LOG = LoggerFactory.getLogger(JavaCompileValidator.class);

    /**
     * 输出目录校验。
     *
     * <p>实测三类形态：多层不存在目录 javac 自动创建并成功（放行，与 saveGenerated 底层
     * FileHelper.writeBytes 的自动建父目录语义同向）；空串 javac 完全静默（call() 返回 true、
     * 零诊断）；已存在的普通文件 javac 内部抛 java.lang.Error 且 collector 零诊断。
     * 后两类必须由本模块拒绝。
     */
    static void validateOutputDir(File outputDir, JavaDiagnosticCollector out) {
        if (outputDir == null)
            return;

        String path = outputDir.getPath();
        if (path.isEmpty()) {
            out.addError(ERR_JAVAC_INVALID_OUTPUT_DIR, ARG_PATH, path);
            return;
        }

        if (outputDir.isFile()) {
            out.addError(ERR_JAVAC_INVALID_OUTPUT_DIR, ARG_PATH, path);
            return;
        }

        // 父路径为普通文件时（如 <file>/sub）javac 报一条 ERROR 后失败；
        // 本模块在 getTask 前显式拒绝，避免把它交给 javac 产生不可预期的失败形态
        File parent = outputDir.getAbsoluteFile().getParentFile();
        while (parent != null && !parent.exists()) {
            parent = parent.getParentFile();
        }
        if (parent != null && !parent.isDirectory()) {
            out.addError(ERR_JAVAC_INVALID_OUTPUT_DIR, ARG_PATH, path);
        }
    }

    /**
     * 编码校验。
     *
     * <p>实测：磁盘输入 + 不存在的字符集名时 javac 返回 call()==true、collector 零诊断、
     * task Writer 0 字节，且**输出目录都不创建**。
     *
     * <p>内存输入不受影响——内存源码覆写 getCharContent，javac 从不解码，
     * 故字符集名校验只对磁盘源文件有意义。
     */
    static Charset validateEncoding(String encoding, JavaDiagnosticCollector out) {
        if (encoding == null || encoding.isEmpty())
            return null;

        if (!Charset.isSupported(encoding)) {
            out.addError(ERR_JAVAC_INVALID_ENCODING, ARG_ENCODING, encoding);
            return null;
        }

        return Charset.forName(encoding);
    }

    /**
     * 源文件可解码性校验。
     *
     * <p>实测：源文件含目标编码不可映射字符时 javac 返回 call()==true、collector 零诊断、
     * task Writer 0 字节，且**产出内容静默损坏的 class 文件**。
     */
    static void validateDecodable(List<File> sourceFiles, Charset charset, JavaDiagnosticCollector out) {
        if (charset == null || sourceFiles == null)
            return;

        for (File file : sourceFiles) {
            if (file == null || !file.isFile())
                continue;

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file.toPath());
            } catch (java.io.IOException e) {
                // 异常对象交给日志保留完整堆栈；诊断面只承载字符串，故取描述走 logFailure
                out.addError(ERR_JAVAC_COMPILE_FAIL,
                        ARG_DETAIL, logReadFailure(file, e));
                continue;
            }

            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);

            try {
                decoder.decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException e) {
                out.addSourceError(ERR_JAVAC_SOURCE_NOT_DECODABLE, file,
                        ARG_SOURCE_FILE, file.getPath(),
                        ARG_ENCODING, charset.name());
            }
        }
    }

    /**
     * 源文件存在性校验。实测 javac 对缺失源文件返回 call()==false 但诊断归到 file manager，
     * 本模块显式校验以便给出带错误码的结构化诊断。
     */
    static void validateSourceFilesExist(List<File> sourceFiles, JavaDiagnosticCollector out) {
        if (sourceFiles == null)
            return;

        for (File file : sourceFiles) {
            if (file == null || file.isFile())
                continue;
            out.addError(ERR_JAVAC_INVALID_OPTION_VALUE,
                    ARG_OPTION, "sourceFiles", ARG_VALUE, String.valueOf(file));
        }
    }

    /**
     * 记录读源码失败并返回可放进诊断面的描述串。
     *
     * <p>拆成独立方法而非在 catch 里直接 {@code LOG.warn(msg, e.getMessage(), e)}：
     * catch 块内同时出现"日志"与"诊断串"两处用途会让堆栈要么进不了诊断面、要么被
     * {@code e.getMessage()} 截断。此处异常对象完整交给日志，诊断面只拿描述。
     */
    private static String logReadFailure(File file, java.io.IOException e) {
        String detail = "read-source-fail:" + e.getMessage();
        LOG.warn("nop.javac.read-source-fail:file={},detail={}", file, detail, e);
        return detail;
    }

    /**
     * 路径存在性校验。javac 对 classpath/sourcepath 类路径不存在**不做任何校验**：
     * 实测返回 call()==true、零诊断、产物照旧，因此必须由本模块自校验。
     */
    static void validatePathsExist(List<File> paths, String optionName, JavaDiagnosticCollector out) {
        if (paths == null)
            return;

        for (File path : paths) {
            if (path == null || !path.exists())
                out.addError(ERR_JAVAC_INVALID_OPTION_VALUE,
                        ARG_OPTION, optionName,
                        ARG_VALUE, String.valueOf(path));
        }
    }

    /**
     * 生成源目录校验。实测 javac 对任意 -s 值（含不存在的、指向普通文件的、不可写的）
     * 全部返回 call()==true、collector 零诊断，完全静默。
     *
     * <p>处置与输出目录一致：不存在的目录自动创建（javac 对 -d 也是自动建，
     * 且 saveGenerated 底层的 FileHelper.writeBytes 同样自动建父目录），
     * 只有"存在但不是目录"与"不可写"才是显式失败。
     */
    static void validateGeneratedSourceDir(File dir, JavaDiagnosticCollector out) {
        if (dir == null)
            return;

        if (dir.isFile()) {
            out.addError(ERR_JAVAC_INVALID_OUTPUT_DIR,
                    ARG_PATH, dir.getPath());
            return;
        }

        if (!dir.exists() && !dir.mkdirs()) {
            out.addError(ERR_JAVAC_INVALID_OUTPUT_DIR,
                    ARG_PATH, dir.getPath());
            return;
        }

        if (dir.exists() && !dir.canWrite())
            out.addError(ERR_JAVAC_INVALID_OUTPUT_DIR, ARG_PATH, dir.getPath());
    }

    /**
     * classpath 存在性校验。实测 javac 对不存在的 classpath 条目返回 call()==true、
     * 零诊断、产物照旧（后续符号解析才可能连带失败，但类缺失本身无诊断）。
     */
    static void validateClassPaths(List<String> classPaths, JavaDiagnosticCollector out) {
        if (classPaths == null)
            return;

        for (String classPath : classPaths) {
            if (classPath == null || classPath.isEmpty())
                continue;
            if (!new File(classPath).exists())
                out.addError(ERR_JAVAC_INVALID_OPTION_VALUE,
                        ARG_OPTION, "-classpath", ARG_VALUE, classPath);
        }
    }

    static void validateImplicitPolicy(String policy, JavaDiagnosticCollector out) {
        if (policy == null)
            return;

        if (!JavaCompileOptions.IMPLICIT_NONE.equals(policy) && !JavaCompileOptions.IMPLICIT_CLASS.equals(policy))
            out.addError(ERR_JAVAC_INVALID_OPTION_VALUE,
                    ARG_OPTION, "-implicit",
                    ARG_VALUE, policy);
    }

    static void validateProcMode(String procMode, JavaDiagnosticCollector out) {
        if (procMode == null)
            return;

        if (!"none".equals(procMode) && !"only".equals(procMode) && !"full".equals(procMode))
            out.addError(ERR_JAVAC_INVALID_OPTION_VALUE,
                    ARG_OPTION, "-proc",
                    ARG_VALUE, procMode);
    }

}