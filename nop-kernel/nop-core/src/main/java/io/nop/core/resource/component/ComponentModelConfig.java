/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.IResourceDslNodeSaver;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectSaver;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ComponentModelConfig {
    private String modelType;

    private String resolveInDir;

    private boolean supportVersion;

    private String primaryFileType;
    private String xdefPath;

    /**
     * 模型可以存在多种存储格式，每种格式对应一个文件类型，采用一种特定的加载器加载
     * <p>
     * fileType与fileExt的区别在于fileExt是最后一个dot字符后面的部分，而fileType是文件名中第一个dot字符后面的部分， 例如my.xpt.xlsx对应的fileType为xpt.xlsx,
     * my.xml对应的fileType为xml。
     */
    private Map<String, LoaderConfig> loaders;


    private Map<String, IComponentTransformer<Object,Object>> transformers;

    public static class LoaderConfig {
        private final String type;
        private final String impPath;
        private final String xdefPath;
        private final Map<String, Object> attributes;
        private final IResourceObjectLoader<Object> loader;
        private final IResourceObjectSaver<Object> saver;
        private final IResourceDslNodeLoader dslNodeLoader;
        private final IResourceDslNodeSaver dslNodeSaver;

        public LoaderConfig(String type,
                            String impPath, String xdefPath,
                            Map<String, Object> attributes,
                            IResourceObjectLoader<Object> loader) {
            this.type = type;
            this.impPath = impPath;
            this.xdefPath = xdefPath;
            this.loader = loader;
            this.attributes = attributes;
            this.saver = loader instanceof IResourceObjectSaver ? (IResourceObjectSaver<Object>) loader : null;
            this.dslNodeLoader = loader instanceof IResourceDslNodeLoader ? (IResourceDslNodeLoader) loader : null;
            this.dslNodeSaver = loader instanceof IResourceDslNodeSaver ? (IResourceDslNodeSaver) loader : null;
        }

        public Object getAttribute(String name) {
            if (attributes == null)
                return null;
            return attributes.get(name);
        }

        public String getType() {
            return type;
        }

        public String getImpPath() {
            return impPath;
        }

        public String getXdefPath() {
            return xdefPath;
        }

        public IResourceObjectLoader<Object> getLoader() {
            return loader;
        }

        public IResourceObjectSaver<Object> getSaver() {
            return saver;
        }

        public IResourceDslNodeLoader getDslNodeLoader() {
            return dslNodeLoader;
        }

        public IResourceDslNodeSaver getDslNodeSaver() {
            return dslNodeSaver;
        }
    }

    public String getPrimaryFileType() {
        return primaryFileType;
    }

    public void setPrimaryFileType(String primaryFileType) {
        this.primaryFileType = primaryFileType;
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

    public Map<String, IComponentTransformer<Object,Object>> getTransformers() {
        return transformers;
    }

    public void setTransformers(Map<String, IComponentTransformer<Object,Object>> transformers) {
        this.transformers = transformers;
    }

    public IComponentTransformer<Object,Object> getTransformer(String transform) {
        if (transformers == null)
            return null;
        return transformers.get(transform);
    }
}