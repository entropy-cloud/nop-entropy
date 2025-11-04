/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.URLResource;
import io.nop.core.resource.scan.ClassPathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProxyConfigGenerator {
    static final Logger LOG = LoggerFactory.getLogger(ProxyConfigGenerator.class);

    public static ProxyConfigGenerator instance() {
        return new ProxyConfigGenerator();
    }

    private ProxyConfig _defaultConfig;

    public ProxyConfig loadDefaultConfig() {
        if (_defaultConfig == null) {
            ProxyConfig config = new ProxyConfig();
            new ClassPathScanner().scanPath("META-INF/native-image/", (path, url) -> {
                if (path.endsWith("proxy-config.json")) {
                    ProxyConfig existing = loadConfig(new URLResource("classpath:" + path, url));
                    config.merge(existing);
                }
            });
            _defaultConfig = config;
        }
        return _defaultConfig;
    }

    public void generateToResource(IResource resource) {
        ProxyConfig config = generate();
        saveConfig(resource, config);
    }

    public void generateDeltaToDir(File dir) {
        generateDeltaToResource(new FileResource(new File(dir, "proxy-config.json")));
    }

    public void generateDeltaToResource(IResource resource) {
        ProxyConfig config = generate();
        config.remove(loadDefaultConfig());

        if (resource.length() > 0) {
            ProxyConfig oldConfig = loadConfig(resource);
            config.merge(oldConfig);
        }
        saveConfig(resource, config);
    }

    ProxyConfig loadConfig(IResource resource) {
        Set<List<String>> list = JsonTool.parseBeanFromResource(resource, Set.class);
        ProxyConfig ret = new ProxyConfig();
        ret.setProxyClasses(list);
        return ret;
    }

    void saveConfig(IResource resource, ProxyConfig config) {
        config.sort();
        String json = JsonTool.serialize(config.getProxyClasses(), true);

        LOG.info("nop.codegen.save-proxy-config:{}", resource);
        ResourceHelper.writeText(resource, json);
    }

    public ProxyConfig generate() {
        ProxyConfig config = new ProxyConfig();

        Set<List<String>> classes = ReflectionManager.instance().getReflectProxies();
        config.setProxyClasses(new LinkedHashSet<>(classes));
        return config;
    }
}
