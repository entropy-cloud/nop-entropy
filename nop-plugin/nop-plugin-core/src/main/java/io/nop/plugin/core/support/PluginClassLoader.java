/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.plugin.core.support;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 优先从当前ClassLoader加载
 */
public class PluginClassLoader extends URLClassLoader {

    private volatile boolean closed;

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);

        if (urls.length == 0) {
            throw new IllegalArgumentException("urls must not be null nor empty");
        }
        if (parent == null) {
            throw new IllegalArgumentException("parent must not be null");
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // has the class loaded already?
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
            try {
                // find the class from given jar urls as in first constructor parameter.
                loadedClass = findClass(name);
            } catch (ClassNotFoundException ignored) {
                // ignore class not found
            }

            if (loadedClass == null) {
                loadedClass = getParent().loadClass(name);
            }

            if (loadedClass == null) {
                throw new ClassNotFoundException("Could not find class " + name + " in classloader nor in parent classloader");
            }
        }

        if (resolve) {
            resolveClass(loadedClass);
        }
        return loadedClass;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> allRes = new LinkedList<>();

        // load resource from this classloader
        Enumeration<URL> thisRes = findResources(name);
        if (thisRes != null) {
            while (thisRes.hasMoreElements()) {
                allRes.add(thisRes.nextElement());
            }
        }

        // then try finding resources from parent classloaders
        if (allRes.isEmpty() && !isLocalResource(name)) {
            Enumeration<URL> parentRes = getParent().getResources(name);
            if (parentRes != null) {
                while (parentRes.hasMoreElements()) {
                    allRes.add(parentRes.nextElement());
                }
            }
        }

        return new Enumeration<>() {
            final Iterator<URL> it = allRes.iterator();

            @Override
            public boolean hasMoreElements() {
                return it.hasNext();
            }

            @Override
            public URL nextElement() {
                return it.next();
            }
        };
    }

    @Override
    public URL getResource(String name) {
        URL res = findResource(name);
        if (res == null) {
            if (isLocalResource(name))
                return null;

            res = super.getResource(name);
        }
        return res;
    }

    protected boolean isLocalResource(String name) {
        // 模型文件仅从当前模块中加载
        if (name.startsWith("/_vfs/") || name.startsWith("/META-INF/services/io.nop."))
            return true;
        return false;
    }

    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
    }

    /**
     * Returns if this classloader has been already closed.
     * <p>
     * Visible for testing because there is no easy way to find out if
     * {@link URLClassLoader} has been closed.
     */
    public boolean isClosed() {
        return closed;
    }
}
