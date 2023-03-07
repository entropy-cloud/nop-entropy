/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResourceObjectLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ComponentModelConfig {
    private String modelType;

    /**
     * 模型可以存在多种存储格式，每种格式对应一个文件类型，采用一种特定的加载器加载
     * <p>
     * fileType与fileExt的区别在于fileExt是最后一个dot字符后面的部分，而fileType是文件名中第一个dot字符后面的部分， 例如my.xpt.xlsx对应的fileType为xpt.xlsx,
     * my.xml对应的fileType为xml。
     */
    private Map<String, IResourceObjectLoader<? extends IComponentModel>> loaders;

    private IComponentGenPathStrategy genPathStrategy;
    private IComponentGenerator generator;

    private Map<String, Function<IComponentModel, IComponentModel>> transformers;

    public ComponentModelConfig modelType(String modelType) {
        this.setModelType(modelType);
        return this;
    }

    public ComponentModelConfig loader(String name, IResourceObjectLoader<? extends IComponentModel> loader) {
        if (loaders == null)
            loaders = new HashMap<>();
        loaders.put(name, loader);
        return this;
    }

    public ComponentModelConfig transformer(String name, Function<IComponentModel, IComponentModel> fn) {
        if (transformers == null)
            transformers = new HashMap<>();
        transformers.put(name, fn);
        return this;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Map<String, IResourceObjectLoader<? extends IComponentModel>> getLoaders() {
        return loaders;
    }

    public void setLoaders(Map<String, IResourceObjectLoader<? extends IComponentModel>> loaders) {
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

    public Map<String, Function<IComponentModel, IComponentModel>> getTransformers() {
        return transformers;
    }

    public void setTransformers(Map<String, Function<IComponentModel, IComponentModel>> transformers) {
        this.transformers = transformers;
    }

    public Function<IComponentModel, IComponentModel> getTransformer(String transform) {
        if (transformers == null)
            return null;
        return transformers.get(transform);
    }
}