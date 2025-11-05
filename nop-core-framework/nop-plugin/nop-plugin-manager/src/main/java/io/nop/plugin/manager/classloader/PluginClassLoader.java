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
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.URLResource;
import io.nop.plugin.api.IPlugin;
import io.nop.plugin.api.NopPluginConstants;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

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
            PluginConfig config = new PluginConfig();
            config.setPluginClassName(getClass().getName());
            return config;
        }

        PluginConfig config = JsonTool.parseBeanFromResource(new URLResource(NopPluginConstants.PLUGIN_CONFIG_FILE, url), PluginConfig.class);
        if (StringHelper.isEmpty(config.getPluginClassName()))
            throw new NopException(ERR_PLUGIN_NO_PLUGIN_CLASS_NAME);
        return config;
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