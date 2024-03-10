/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaLibConfig {
    private List<File> libDirs = new ArrayList<>();
    private List<File> classesDirs = new ArrayList<>();
    private List<File> sourceDirs = new ArrayList<>();
    private File cacheDir;

    public void addLibDir(File dir) {
        libDirs.add(dir);
    }

    public void addClassesDir(File dir) {
        classesDirs.add(dir);
    }

    public void addSourceDir(File dir) {
        sourceDirs.add(dir);
    }

    public List<File> getLibDirs() {
        return libDirs;
    }

    public void setLibDirs(List<File> libDirs) {
        this.libDirs = libDirs;
    }

    public List<File> getClassesDirs() {
        return classesDirs;
    }

    public void setClassesDirs(List<File> classesDirs) {
        this.classesDirs = classesDirs;
    }

    public List<File> getSourceDirs() {
        return sourceDirs;
    }

    public void setSourceDirs(List<File> sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(File cacheDir) {
        this.cacheDir = cacheDir;
    }
}
