/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.jdk;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件式编译的选项对象。未设置项一律保持"与今天内存编译路径等价"的默认行为，
 * 该等价性由选项装配缝的断言测试锁定。
 */
public class JavaCompileOptions {
    public static final String IMPLICIT_NONE = "none";
    public static final String IMPLICIT_CLASS = "class";

    private File outputDir;
    private List<String> classPaths;
    private String release;
    private String encoding;
    private List<File> sourcePath;
    private String implicitPolicy;
    private String procMode;
    private List<String> processors;
    private List<File> processorPath;
    private File generatedSourceDir;
    private List<File> modulePath;
    private List<String> extraOptions;

    public File getOutputDir() {
        return outputDir;
    }

    public JavaCompileOptions setOutputDir(File outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public List<String> getClassPaths() {
        return classPaths;
    }

    public JavaCompileOptions setClassPaths(List<String> classPaths) {
        this.classPaths = classPaths;
        return this;
    }

    public String getRelease() {
        return release;
    }

    public JavaCompileOptions setRelease(String release) {
        this.release = release;
        return this;
    }

    public String getEncoding() {
        return encoding;
    }

    public JavaCompileOptions setEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    public List<File> getSourcePath() {
        return sourcePath;
    }

    public JavaCompileOptions setSourcePath(List<File> sourcePath) {
        this.sourcePath = sourcePath;
        return this;
    }

    public String getImplicitPolicy() {
        return implicitPolicy;
    }

    public JavaCompileOptions setImplicitPolicy(String implicitPolicy) {
        this.implicitPolicy = implicitPolicy;
        return this;
    }

    public String getProcMode() {
        return procMode;
    }

    public JavaCompileOptions setProcMode(String procMode) {
        this.procMode = procMode;
        return this;
    }

    public List<String> getProcessors() {
        return processors;
    }

    public JavaCompileOptions setProcessors(List<String> processors) {
        this.processors = processors;
        return this;
    }

    public List<File> getProcessorPath() {
        return processorPath;
    }

    public JavaCompileOptions setProcessorPath(List<File> processorPath) {
        this.processorPath = processorPath;
        return this;
    }

    public File getGeneratedSourceDir() {
        return generatedSourceDir;
    }

    public JavaCompileOptions setGeneratedSourceDir(File generatedSourceDir) {
        this.generatedSourceDir = generatedSourceDir;
        return this;
    }

    public List<File> getModulePath() {
        return modulePath;
    }

    public JavaCompileOptions setModulePath(List<File> modulePath) {
        this.modulePath = modulePath;
        return this;
    }

    public List<String> getExtraOptions() {
        return extraOptions;
    }

    public JavaCompileOptions setExtraOptions(List<String> extraOptions) {
        this.extraOptions = extraOptions;
        return this;
    }

    List<String> effectiveClassPaths() {
        return classPaths != null ? classPaths : new ArrayList<>();
    }
}