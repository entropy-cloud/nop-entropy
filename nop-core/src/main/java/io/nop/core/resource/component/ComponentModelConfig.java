/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResourceObjectLoader;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ComponentModelConfig {
    private String modelType;

    private String resolveInDir;
    private IResourceObjectLoader<? extends IComponentModel> resolveDefaultLoader;

    private boolean supportVersion;

    private String xdefPath;
    private String impPath;

    /**
     * 模型可以存在多种存储格式，每种格式对应一个文件类型，采用一种特定的加载器加载
     * <p>
     * fileType与fileExt的区别在于fileExt是最后一个dot字符后面的部分，而fileType是文件名中第一个dot字符后面的部分， 例如my.xpt.xlsx对应的fileType为xpt.xlsx,
     * my.xml对应的fileType为xml。
     */
    private Map<String, LoaderConfig> loaders;

    private IComponentGenPathStrategy genPathStrategy;
    private IComponentGenerator generator;

    private Map<String, IComponentTransformer> transformers;

    public static class LoaderConfig {
        private final String impPath;
        private final String xdefPath;
        private final IResourceObjectLoader<? extends IComponentModel> loader;

        public LoaderConfig(String impPath, String xdefPath, IResourceObjectLoader<? extends IComponentModel> loader) {
            this.impPath = impPath;
            this.xdefPath = xdefPath;
            this.loader = loader;
        }

        public String getImpPath() {
            return impPath;
        }

        public String getXdefPath() {
            return xdefPath;
        }

        public IResourceObjectLoader<? extends IComponentModel> getLoader() {
            return loader;
        }
    }

    public LoaderConfig getLoader(String fileType) {
        if (loaders == null)
            return null;
        return loaders.get(fileType);
    }

    public ComponentModelConfig loader(String fileType, LoaderConfig loaderConfig) {
        if (loaders == null)
            loaders = new HashMap<>();
        loaders.put(fileType, loaderConfig);
        return this;
    }

    public boolean isSupportVersion() {
        return supportVersion;
    }

    public void setSupportVersion(boolean supportVersion) {
        this.supportVersion = supportVersion;
    }

    public String getXdefPath() {
        return xdefPath;
    }

    public void setXdefPath(String xdefPath) {
        this.xdefPath = xdefPath;
    }

    public ComponentModelConfig modelType(String modelType) {
        this.setModelType(modelType);
        return this;
    }

    public ComponentModelConfig transformer(String name, IComponentTransformer fn) {
        if (transformers == null)
            transformers = new LinkedHashMap<>();
        transformers.put(name, fn);
        return this;
    }

    public String getResolveInDir() {
        return resolveInDir;
    }

    public void setResolveInDir(String resolveInDir) {
        this.resolveInDir = resolveInDir;
    }

    public IResourceObjectLoader<? extends IComponentModel> getResolveDefaultLoader() {
        return resolveDefaultLoader;
    }

    public void setResolveDefaultLoader(IResourceObjectLoader<? extends IComponentModel> resolveDefaultLoader) {
        this.resolveDefaultLoader = resolveDefaultLoader;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Map<String, LoaderConfig> getLoaders() {
        return loaders;
    }

    public void setLoaders(Map<String, LoaderConfig> loaders) {
        this.loaders = loaders;
    }

    public IComponentGenPathStrategy getGenPathStrategy() {
        return genPathStrategy;
    }

    public void setGenPathStrategy(IComponentGenPathStrategy genPathStrategy) {
        this.genPathStrategy = genPathStrategy;
    }

    public IComponentGenerator getGenerator() {
        return generator;
    }

    public void setGenerator(IComponentGenerator generator) {
        this.generator = generator;
    }

    public Map<String, IComponentTransformer> getTransformers() {
        return transformers;
    }

    public void setTransformers(Map<String, IComponentTransformer> transformers) {
        this.transformers = transformers;
    }

    public IComponentTransformer getTransformer(String transform) {
        if (transformers == null)
            return null;
        return transformers.get(transform);
    }

    public String getImpPath() {
        return impPath;
    }

    public void setImpPath(String impPath) {
        this.impPath = impPath;
    }
}