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
package io.nop.plugin.manager.classloader;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.URLHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.URLResource;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.NopPluginConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_NO_PLUGIN_CLASS_NAME;

/**
 * 满足匹配模式的资源从外部ClassLoader加载，否则从URL和JDK加载
 */
public class PluginClassLoader extends URLClassLoader {
    private PluginConfig pluginConfig;
    private ClassLoader importClassLoader;
    private IPlugin plugin;

    private volatile boolean closed;

    public PluginClassLoader(URL[] urls, ClassLoader importClassLoader) {
        super(urls, String.class.getClassLoader());

        if (urls.length == 0) {
            throw new IllegalArgumentException("urls must not be null nor empty");
        }
        if (importClassLoader == null) {
            throw new IllegalArgumentException("parent must not be null");
        }
        this.importClassLoader = importClassLoader;

        this.pluginConfig = loadPluginConfig();
    }

    PluginConfig loadPluginConfig() {
        URL url = getResource(NopPluginConstants.PLUGIN_CONFIG_FILE);
        if (url == null) {
            // URLClassLoader 的 getResource 对以 "/" 开头的资源名在 jar/dir 中无法解析，
            // 回退到去掉前导 "/" 的变体（classpath 资源名是包相对路径）。
            String path = NopPluginConstants.PLUGIN_CONFIG_FILE;
            if (path.startsWith("/"))
                path = path.substring(1);
            url = getResource(path);
        }
        if (url == null) {
            // 缺失 plugin.json 时不得以 PluginClassLoader 自身兜底：loadPlugin 的 (IPlugin) 强转
            // 只会抛出指向性不明的 ClassCastException（或构造失败），必须抛明确错误码
            throw new NopException(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME);
        }

        PluginConfig config = parsePluginConfig(url);
        if (StringHelper.isEmpty(config.getPluginClassName()))
            throw new NopException(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME);
        return config;
    }

    private PluginConfig parsePluginConfig(URL url) {
        if (URLHelper.isJarURL(url)) {
            // Windows 文件锁（JDK JarFileFactory 缓存）：jar: URL 读取会把 JarFile 句柄缓存进
            // JarFileFactory，PluginClassLoader.close() 也无法释放该句柄，jar 文件删除会失败直到
            // JVM 退出。改用自有 JarFile 读取 plugin.json 并立即关闭，不经过 jar: 协议。
            String file = url.getFile();
            int pos = file.indexOf("!/");
            if (pos < 0) {
                return JsonTool.parseBeanFromResource(
                        new URLResource(NopPluginConstants.PLUGIN_CONFIG_FILE, url), PluginConfig.class);
            }
            String entryName = file.substring(pos + 2);
            if (entryName.startsWith("/"))
                entryName = entryName.substring(1);
            try {
                File jarFile = new File(new URL(file.substring(0, pos)).toURI());
                try (JarFile jar = new JarFile(jarFile)) {
                    JarEntry entry = jar.getJarEntry(entryName);
                    if (entry == null) {
                        throw new NopException(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME);
                    }
                    try (InputStream in = jar.getInputStream(entry)) {
                        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        return JsonTool.parseBeanFromText(text, PluginConfig.class);
                    }
                }
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
        return JsonTool.parseBeanFromResource(
                new URLResource(NopPluginConstants.PLUGIN_CONFIG_FILE, url), PluginConfig.class);
    }

    public IPlugin loadPlugin() {
        return (IPlugin) ClassHelper.newInstance(pluginConfig.getPluginClassName(), this);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    protected ClassLoader getImportClassLoader() {
        return importClassLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (pluginConfig != null && pluginConfig.shouldImportClass(name))
            return getImportClassLoader().loadClass(name);

        return super.loadClass(name, resolve);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (pluginConfig != null && pluginConfig.shouldImportClass(name))
            return getImportClassLoader().getResources(name);

        return super.getResources(name);
    }

    @Override
    public URL getResource(String name) {
        if (pluginConfig != null && pluginConfig.shouldImportClass(name))
            return getImportClassLoader().getResource(name);

        return super.getResource(name);
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