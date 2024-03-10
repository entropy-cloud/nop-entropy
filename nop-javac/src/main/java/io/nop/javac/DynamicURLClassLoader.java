/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import io.nop.api.core.exceptions.NopException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

public class DynamicURLClassLoader extends URLClassLoader implements IDynamicClassLoader {
    private boolean closed;

    private final String name;

    public DynamicURLClassLoader(String name, ClassLoader parent) {
        super(new URL[0], parent);
        this.name = name;
    }

    public String toString() {
        return DynamicURLClassLoader.class.getName() + "[name=" + name + "]";
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        super.close();
    }

    public void addJarDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    addFile(file);
                } else {
                    addJarDir(file);
                }
            }
        }
    }

    public void addFile(File file) {
        try {
            super.addURL(file.toURI().toURL());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public void addClassesDir(File dir) {
        addFile(dir);
    }
}