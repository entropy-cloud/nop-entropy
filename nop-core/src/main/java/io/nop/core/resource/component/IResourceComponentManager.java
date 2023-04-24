/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.IComponentModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.deps.ResourceDependencySet;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.core.CoreErrors.ARG_FILE_TYPE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_UNKNOWN_MODEL_FILE_TYPE;

/**
 * 管理所有模型文件，并缓存编译和生成结果。每一种模型（Model）可以采用多种文件类型（fileType）来存储，可以生成不同格式（genFormat）的组件 （GeneratedComponent）。
 * <p>
 * IResource --[parse] --> IComponentModel --[generate]--> IGeneratedComponent
 * </p>
 * 内部通过{@link io.nop.core.resource.deps.ResourceDependsManager}来管理资源文件之间的依赖关系以及编译缓存。
 * 模型文件和组件文件都支持缓存机制，当它们依赖的文件没有发生变化时，直接返回缓存结果。
 */
public interface IResourceComponentManager extends IResourceDependencyManager {
    /**
     * 每一种模型都对应一个特定的模型类型。
     *
     * @param config 模型解析相关配置
     */
    ICancellable registerComponentModelConfig(ComponentModelConfig config);

    Runnable registerComponentModelLoader(String modelType, String fileType,
                                          IResourceObjectLoader<? extends IComponentModel> loader, boolean replace);

    Runnable registerComponentModelTransformer(String fromModelType, String toModelType,
                                               IComponentTransformer<?,?> transformer, boolean replace);

    ComponentModelConfig getModelConfigByModelPath(String path);

    ComponentModelLoader getComponentModelLoader(String fileType);

    default ComponentModelLoader requireComponentModelLoader(String fileType) {
        ComponentModelLoader loader = getComponentModelLoader(fileType);
        if(loader == null)
            throw new NopException(ERR_COMPONENT_UNKNOWN_MODEL_FILE_TYPE)
                    .param(ARG_FILE_TYPE, fileType);
        return loader;
    }

    void clearCache(String modelType);

    IComponentModel loadComponentModel(String modelPath);

    /**
     * 根据componentModel装载模型，并转换为指定类型的模型对象
     *
     * @param modelPath 模型资源路径
     * @param transform 转换类型
     */
    IComponentModel loadComponentModel(String modelPath, String transform);

    /**
     * 每个模型对应于一个唯一的、确定的资源url。资源url格式为 resourcePath?paramName=paramValue 例如：/a/b.xmeta?transform=xdef&sub=MyObject。
     * 通过这种方式可以用统一的方式来获取基础模型、转换后模型和子模型。
     *
     * <p>
     * resourcePath对应基础模型的装载路径。并支持如下装载参数：
     * <p>
     * 1. transform: 转换为指定格式的模型对象
     * </p>
     * <p>
     * 2. sub: 对于实现了I{@link ICompositeComponentModel}接口的复杂组件，可以通过sub来传递子组件名称来获取内部子组件
     * </p>
     *
     * @param modelUrl 源url格式为 resourcePath?paramName=paramValue，参数为可选部分
     */
    IComponentModel loadComponentModelByUrl(String modelUrl);

    String buildComponentPath(String modelPath, String genFormat);

    /**
     * 根据componentPath先确定模型对象，然后再根据模型和genFormat生成对应组件文件
     *
     * @param componentPath 可以通过IComponentGenStrategy解析得到modelPath和genFormat
     */
    IGeneratedComponent loadGeneratedComponent(String componentPath);

    /**
     * 从预编译缓存中装载对象
     */
    <T> T loadPrecompiledObject(String resourcePath);

    boolean isDependencyChanged(String resourcePath);

    boolean isAnyDependsChange(Set<String> depends);

    /**
     * 根据task处理过程中访问的资源文件情况，把它们收集到依赖集合对象中
     *
     * @param resourcePath 正在收集此资源文件的依赖集合
     * @param task         处理任务，在其中通过traceDepends函数来记录依赖文件
     * @return task的返回结果
     */
    <T> T collectDepends(String resourcePath, Supplier<T> task);

    /**
     * 根据task处理过程中访问的资源文件情况，把它们收集到依赖集合对象中
     *
     * @param dep  正在收集此资源文件的依赖集合
     * @param task 处理任务，在其中通过traceDepends函数来记录依赖文件
     * @return task的返回结果
     */
    <T> T collectDependsTo(ResourceDependencySet dep, Supplier<T> task);

    /**
     * 忽略task执行过程中的依赖关系，不把它们追加到当前的依赖集合中
     */
    <T> T ignoreDepends(Supplier<T> task);

    /**
     * 将资源文件追加到当前上下文的依赖集合中
     *
     * @param depResourcePath 被依赖的文件
     */
    void traceDepends(String depResourcePath);

    void traceAllDepends(Set<String> depends);

    ResourceDependencySet getResourceDepends(String resourcePath);

    /**
     * modelPath可能是一个由loader识别的特殊路径，并不对应到具体的IResource对象
     *
     * @param modelPath 模型路径
     * @return
     */
    ResourceDependencySet getModelDepends(String modelPath);

    /**
     * 递归收集所有依赖对象，并打印到调试文本中
     *
     * @param deps 依赖对象
     */
    String dumpDependsSet(ResourceDependencySet deps);

    void clearAllCache();
}