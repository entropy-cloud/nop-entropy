/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.IFreezable;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.IObjectChangeDetectable;
import io.nop.core.resource.cache.IResourceLoadingCache;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.core.resource.deps.DefaultResourceChangeChecker;
import io.nop.core.resource.deps.IResourceChangeChecker;
import io.nop.core.resource.deps.ResourceChangeCheckResult;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.resource.deps.ResourceDependsHelper;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.core.resource.tenant.ResourceTenantManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.nop.core.CoreErrors.ARG_COMPONENT_PATH;
import static io.nop.core.CoreErrors.ARG_FILE_TYPE;
import static io.nop.core.CoreErrors.ARG_FROM_MODEL_TYPE;
import static io.nop.core.CoreErrors.ARG_MODEL_TYPE;
import static io.nop.core.CoreErrors.ARG_MODEL_TYPE2;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ARG_TO_MODEL_TYPE;
import static io.nop.core.CoreErrors.ARG_TRANSFORM;
import static io.nop.core.CoreErrors.ERR_COMPONENT_INVALID_MODEL_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_MODEL_FILE_TYPE_CONFLICT;
import static io.nop.core.CoreErrors.ERR_COMPONENT_MODEL_TRANSFORMER_ALREADY_EXISTS;
import static io.nop.core.CoreErrors.ERR_COMPONENT_NOT_COMPOSITE_COMPONENT;
import static io.nop.core.CoreErrors.ERR_COMPONENT_NO_GEN_PATH_STRATEGY;
import static io.nop.core.CoreErrors.ERR_COMPONENT_UNDEFINED_COMPONENT_MODEL_TRANSFORM;
import static io.nop.core.CoreErrors.ERR_COMPONENT_UNKNOWN_FILE_TYPE_FOR_MODEL_TYPE;
import static io.nop.core.CoreErrors.ERR_COMPONENT_UNKNOWN_MODEL_FILE_TYPE;
import static io.nop.core.resource.component.version.ResourceVersionHelper.isVersionFile;

@GlobalInstance
public class ResourceComponentManager implements IResourceComponentManager, IConfigRefreshable, IResourceChangeChecker {
    static final Logger LOG = LoggerFactory.getLogger(ResourceComponentManager.class);

    private static IResourceComponentManager _instance = new ResourceComponentManager(true);

    public static IResourceComponentManager instance() {
        return _instance;
    }

    public static void registerInstance(IResourceComponentManager instance) {
        _instance = instance;
    }

    private IResourceDependencyManager dependsManager = ResourceTenantManager.instance().makeDependencyManager(this);

    private Map<String, ComponentModelConfig> modelTypeConfigs = new ConcurrentHashMap<>();
    private Map<String, ComponentModelLoader> fileTypeLoaders = new ConcurrentHashMap<>();
    private Map<String, List<ComponentModelConfig>> fileTypeToGenerators = new ConcurrentHashMap<>();

    private Map<Pair<String, String>, IComponentTransformer> modelTypeTransformers = new ConcurrentHashMap<>();

    private Map<String, IResourceLoadingCache<ComponentCacheEntry>> modelCaches = new ConcurrentHashMap<>();

    private IResourceChangeChecker changeChecker = DefaultResourceChangeChecker.INSTANCE;

    private boolean registerCache;

    public ResourceComponentManager(boolean registerCache) {
        this.registerCache = registerCache;
    }

    class ModelLoader implements IResourceObjectLoader<ComponentCacheEntry> {
        private final String modelType;

        public ModelLoader(String modelType) {
            this.modelType = modelType;
        }

        @Override
        public ComponentCacheEntry loadObjectFromPath(String path) {
            Pair<String, IResourceObjectLoader<? extends IComponentModel>> pair = resolveModelLoader(path, modelType);
            if (pair == null)
                return null;

            IComponentModel model;
            if (!ResourceTenantManager.supportTenant(path)) {
                model = ContextProvider.runWithoutTenantId(() -> pair.getValue().loadObjectFromPath(pair.getKey()));
            } else {
                model = pair.getValue().loadObjectFromPath(pair.getKey());
            }

            if (model instanceof IFreezable) {
                IFreezable freezable = (IFreezable) model;
                if (!freezable.frozen())
                    freezable.freeze(true);
            }

            ComponentCacheEntry entry = new ComponentCacheEntry();
            entry.model = model;
            return entry;
        }
    }

    class GenComponentLoader implements IResourceObjectLoader<IGeneratedComponent> {
        private final ComponentModelConfig config;

        public GenComponentLoader(ComponentModelConfig config) {
            this.config = config;
        }

        @Override
        public IGeneratedComponent loadObjectFromPath(String path) {
            ComponentGenPath genPath = config.getGenPathStrategy().parseComponentPath(path);
            IComponentModel model = loadComponentModel(genPath.getModelPath());
            if (!ResourceTenantManager.supportTenant(path)) {
                return ContextProvider.runWithoutTenantId(() -> config.getGenerator().generateComponent(model,
                        genPath.getGenFormat(), ResourceComponentManager.this));
            }
            return config.getGenerator().generateComponent(model, genPath.getGenFormat(),
                    ResourceComponentManager.this);
        }
    }

    @Override
    public void refreshConfig() {
        for (IResourceLoadingCache<?> cache : this.modelCaches.values()) {
            cache.refreshConfig();
        }
    }

    @Override
    public void clearAllCache() {
        for (IResourceLoadingCache<?> cache : this.modelCaches.values()) {
            cache.clear();
        }

        // 清空资源文件的依赖关系缓存
        this.dependsManager.clearDependencies();
    }

    public void setChangeChecker(IResourceChangeChecker changeChecker) {
        this.changeChecker = Guard.notNull(changeChecker, "changeChecker");
    }

    @Override
    public void clearDependencies() {
        dependsManager.clearDependencies();
    }

    @Override
    public ResourceChangeCheckResult checkChanged(String resourcePath, long lastModified) {
        return changeChecker.checkChanged(resourcePath, lastModified);
    }

    @Override
    public boolean isDependencyChanged(String path) {
        return dependsManager.isDependencyChanged(path);
    }

    @Override
    public boolean isAnyDependsChange(Collection<ResourceDependencySet> depends) {
        return dependsManager.isAnyDependsChange(depends);
    }

    Pair<String, IResourceObjectLoader<? extends IComponentModel>> resolveModelLoader(String path, String modelType) {
        if (path.startsWith(ResourceConstants.RESOLVE_PREFIX)) {
            int pos = path.indexOf(':');
            String subName = path.substring(pos + 1);
            ComponentModelConfig config = modelTypeConfigs.get(modelType);
            String dir = config.getResolveInDir();
            String fullPath = StringHelper.appendPath(dir, subName);
            for (Map.Entry<String, ComponentModelConfig.LoaderConfig> entry : config.getLoaders().entrySet()) {
                String fileType = entry.getKey();
                IResource resource = VirtualFileSystem.instance().getResource(fullPath + "." + fileType);
                if (resource.exists())
                    return Pair.of(resource.getPath(), entry.getValue().getLoader());
                LOG.info("nop.resource.try-resolve-file-not-exists:{}", resource.getPath());
            }

            // 如果支持版本且当前传入的路径没有包含版本，则尝试查找版本号最大的数据返回
            if (config.isSupportVersion() && !ResourceVersionHelper.endsWithNumberVersion(subName)) {
                for (Map.Entry<String, ComponentModelConfig.LoaderConfig> entry : config.getLoaders().entrySet()) {
                    String fileType = entry.getKey();
                    Collection<? extends IResource> resources = VirtualFileSystem.instance().getChildren(fullPath);
                    resources = resources.stream().filter(res -> isVersionFile(res, fileType))
                            .sorted(ResourceVersionHelper::compareResourceVersion).collect(Collectors.toList());
                    if (!resources.isEmpty()) {
                        IResource resource = CollectionHelper.first(resources);
                        return Pair.of(resource.getPath(), entry.getValue().getLoader());
                    }
                }
            }

            if (config.getResolveDefaultLoader() != null) {
                return Pair.of(path, config.getResolveDefaultLoader());
            } else {
                return null;
            }
        } else {
            String fileType = StringHelper.fileType(path);
            return Pair.of(path, requireLoader(modelType, fileType));
        }
    }

    @Override
    public ICancellable registerComponentModelConfig(ComponentModelConfig config) {
        LOG.info("ResourceComponentManager.registerComponentModelConfig:modelType={}", config.getModelType());

        Cancellable cancellable = new Cancellable();
        cancellable.appendOnCancelTask(() -> {
            LOG.info("ResourceComponentManager.unregisterComponentModelConfig:modelType={}", config.getModelType());
        });

        String modelType = config.getModelType();
        Guard.notEmpty(config.getModelType(), "modelType");
        Guard.checkArgument(!modelTypeConfigs.containsKey(modelType),
                "component with the same model type already exists", modelType);
        Guard.notEmpty(config.getLoaders(), "model type loaders");
        modelTypeConfigs.put(modelType, config);

        cancellable.appendOnCancel(r -> modelTypeConfigs.remove(modelType, config));

        for (Map.Entry<String, ComponentModelConfig.LoaderConfig> entry : config.getLoaders()
                .entrySet()) {
            String fileType = entry.getKey();
            cancellable.appendOnCancelTask(registerComponentModelLoader(modelType, fileType, entry.getValue().getLoader(), false));
        }

        if (config.getTransformers() != null) {
            for (Map.Entry<String, IComponentTransformer> entry : config.getTransformers()
                    .entrySet()) {
                cancellable.appendOnCancelTask(registerComponentModelTransformer(config.getModelType(), entry.getKey(),
                        entry.getValue(), false));
            }
        }

        if (config.getGenPathStrategy() != null) {
            Set<String> fileTypes = config.getGenPathStrategy().getGenFileTypes();
            Guard.notEmpty(fileTypes, "component gen fileTypes should not be empty");
            for (String fileType : fileTypes) {
                addFileTypeToGenerator(cancellable, fileType, config);
            }
        }

        return cancellable;
    }

    @Override
    public Runnable registerComponentModelLoader(String modelType, String fileType,
                                                 IResourceObjectLoader<? extends IComponentModel> loader, boolean replace) {
        Guard.notEmpty(modelType, "modelType");
        Guard.notEmpty(fileType, "fileType");
        Guard.notNull(loader, "loader");

        ComponentModelLoader modelLoader = new ComponentModelLoader(modelType, loader);
        if (replace) {
            fileTypeLoaders.put(fileType, modelLoader);
        } else {
            ComponentModelLoader old = fileTypeLoaders.putIfAbsent(fileType, modelLoader);
            if (old != null) {
                throw new NopException(ERR_COMPONENT_MODEL_FILE_TYPE_CONFLICT).param(ARG_FILE_TYPE, fileType)
                        .param(ARG_MODEL_TYPE, old.getModelType()).param(ARG_MODEL_TYPE2, modelType);
            }
        }
        return () -> fileTypeLoaders.remove(fileType, modelLoader);
    }

    @Override
    public Runnable registerComponentModelTransformer(String fromModelType, String toModelType,
                                                      IComponentTransformer transformer, boolean replace) {
        Guard.notEmpty(fromModelType, "fromModelType");
        Guard.notEmpty(toModelType, "toModelType");
        Guard.notEmpty(transformer, "transformer");

        Pair<String, String> key = Pair.of(fromModelType, toModelType);
        if (replace) {
            modelTypeTransformers.put(key, transformer);
        } else {
            IComponentTransformer old = modelTypeTransformers.putIfAbsent(Pair.of(fromModelType, toModelType), transformer);
            if (old != null && replace)
                throw new NopException(ERR_COMPONENT_MODEL_TRANSFORMER_ALREADY_EXISTS)
                        .param(ARG_FROM_MODEL_TYPE, fromModelType).param(ARG_TO_MODEL_TYPE, toModelType);
        }

        return () -> modelTypeTransformers.remove(key, transformer);
    }

    private void addFileTypeToGenerator(Cancellable cancellable, String fileType, ComponentModelConfig config) {
        if (fileType.startsWith(".") || StringHelper.countChar(fileType, '.') > 2)
            throw new NopException(ERR_COMPONENT_UNKNOWN_FILE_TYPE_FOR_MODEL_TYPE).param(ARG_FILE_TYPE, fileType)
                    .param(ARG_MODEL_TYPE, config.getModelType());

        List<ComponentModelConfig> list = fileTypeToGenerators.computeIfAbsent(fileType,
                k -> new CopyOnWriteArrayList<>());
        list.add(config);

        cancellable.appendOnCancel(r -> list.remove(config));
    }

    @Override
    public Map<String, ComponentModelConfig> getAllModelConfigs() {
        return modelTypeConfigs;
    }

    @Override
    public IComponentModel loadComponentModelByUrl(String modelUrl) {
        String resourcePath = modelUrl;
        String transform = null;
        String subName = null;

        int pos = resourcePath.indexOf('#');
        if (pos > 0) {
            subName = resourcePath.substring(pos + 1);
            resourcePath = resourcePath.substring(0, pos);
        }
        pos = resourcePath.indexOf('?');
        if (pos > 0) {
            transform = resourcePath.substring(0, pos);
            resourcePath = resourcePath.substring(0, pos);
        }

        IComponentModel model;
        if (!StringHelper.isEmpty(transform)) {
            model = loadComponentModel(resourcePath, transform);
        } else {
            model = loadComponentModel(resourcePath);
        }
        if (!StringHelper.isEmpty(subName)) {
            model = getSubComponent(resourcePath, model, subName);
        }
        return model;
    }

    IComponentModel getSubComponent(String resourcePath, IComponentModel model, String subName) {
        if (!(model instanceof ICompositeComponentModel))
            throw new NopException(ERR_COMPONENT_NOT_COMPOSITE_COMPONENT).param(ARG_COMPONENT_PATH, resourcePath);
        return ((ICompositeComponentModel) model).getSubComponent(subName);
    }

    @Override
    public IComponentModel loadComponentModel(String resourcePath, String transform) {
        String modelType = findModelTypeFromPath(resourcePath);
        IComponentTransformer transformer = getTransformer(modelType, transform);

        IResourceLoadingCache<ComponentCacheEntry> cache = makeModelCache(modelType, resourcePath);
        ComponentCacheEntry entry = cache.require(resourcePath);
        if (StringHelper.isEmpty(transform) || modelType.equals(transform))
            return (IComponentModel) entry.model;

        return entry.transformed.computeIfAbsent(transform, k -> transformer.transform((IComponentModel) entry.model));
    }

    private IComponentTransformer getTransformer(String modelType, String transform) {

        if (!StringHelper.isEmpty(transform) && !modelType.equals(transform)) {
            IComponentTransformer transformer = modelTypeTransformers
                    .get(Pair.of(modelType, transform));
            if (transformer == null)
                throw new NopException(ERR_COMPONENT_UNDEFINED_COMPONENT_MODEL_TRANSFORM)
                        .param(ARG_MODEL_TYPE, modelType).param(ARG_TRANSFORM, transform);
            return transformer;
        } else {
            return null;
        }
    }

    @Override
    public void clearCache(String modelType) {
        IResourceLoadingCache<?> cache = modelCaches.get(modelType);
        if (cache != null)
            cache.clear();
    }

    public void removeCachedModel(String path) {
        ComponentModelConfig config = getModelConfigByModelPath(path);
        IResourceLoadingCache<?> cache = getModelCache(config.getModelType());
        if (cache != null) {
            cache.remove(path);
        }
    }

    protected String findModelTypeFromPath(String resourcePath) {
        String fileType = StringHelper.fileType(resourcePath);
        ComponentModelLoader loader = findByFileType(fileTypeLoaders, fileType);
        if (loader != null)
            return loader.getModelType();

        ComponentModelConfig config = requireModelConfigByModelPath(resourcePath);
        return config.getModelType();
    }

    @Override
    public IComponentModel loadComponentModel(String resourcePath) {
        String modelType = findModelTypeFromPath(resourcePath);
        IResourceLoadingCache<ComponentCacheEntry> cache = makeModelCache(modelType, resourcePath);
        return (IComponentModel) cache.require(resourcePath).model;
    }

    @Override
    public IComponentModel parseComponentModel(IResource resource) {
        ComponentModelConfig config = requireModelConfigByModelPath(resource.getPath());
        Pair<String, IResourceObjectLoader<? extends IComponentModel>> pair =
                resolveModelLoader(resource.getPath(), config.getModelType());
        if (pair == null)
            throw new IllegalArgumentException("nop.err.unsupported-resource-file:" + resource.getPath());
        return pair.getRight().parseFromResource(resource);
    }

    @Override
    public IComponentModel parseComponentModel(IResource resource, String transform) {
        ComponentModelConfig config = requireModelConfigByModelPath(resource.getPath());
        Pair<String, IResourceObjectLoader<? extends IComponentModel>> pair =
                resolveModelLoader(resource.getPath(), config.getModelType());
        if (pair == null)
            throw new IllegalArgumentException("nop.err.unsupported-resource-file:" + resource.getPath());

        IComponentModel model = pair.getRight().parseFromResource(resource);

        IComponentTransformer transformer = getTransformer(config.getModelType(), transform);
        if (transformer == null)
            return model;
        return transformer.transform(model);
    }

    static class ComponentCacheEntry implements IObjectChangeDetectable {
        Object model;
        Map<String, IComponentModel> transformed = new ConcurrentHashMap<>();

        @Override
        public boolean isObjectChanged() {
            if (model instanceof IObjectChangeDetectable)
                return ((IObjectChangeDetectable) model).isObjectChanged();
            return false;
        }
    }

    private IResourceLoadingCache<ComponentCacheEntry> makeModelCache(String modelType, String path) {
        Map<String, IResourceLoadingCache<ComponentCacheEntry>> caches = modelCaches;

        return caches.computeIfAbsent(modelType, k -> {
            String name = "model-cache-" + modelType;
            IResourceLoadingCache<ComponentCacheEntry> cache = ResourceTenantManager.instance()
                    .makeLoadingCache(name, new ModelLoader(modelType), null);
            if (registerCache)
                GlobalCacheRegistry.instance().register(cache);
            return cache;
        });
    }

    private IResourceLoadingCache<ComponentCacheEntry> getModelCache(String modelType) {
        return modelCaches.get(modelType);
    }

//    private ComponentModelConfig requireModelConfigByModelType(String modelType) {
//        ComponentModelConfig config = modelTypeConfigs.get(modelType);
//        if (config == null)
//            throw new NopException(ERR_COMPONENT_UNKNOWN_MODEL_TYPE).param(ARG_MODEL_TYPE, modelType);
//        return config;
//    }

    @Override
    public ComponentModelConfig getModelConfigByModelPath(String path) {
        if (path.startsWith(ResourceConstants.RESOLVE_PREFIX)) {
            int pos = path.indexOf(':');
            if (pos < 0)
                throw new NopException(ERR_COMPONENT_INVALID_MODEL_PATH)
                        .param(ARG_RESOURCE_PATH, path);
            String modelType = path.substring(ResourceConstants.RESOLVE_PREFIX.length(), pos);
            return modelTypeConfigs.get(modelType);
        }

        ComponentModelLoader loader = findByFileType(fileTypeLoaders, StringHelper.fileType(path));
        return loader == null ? null : modelTypeConfigs.get(loader.getModelType());
    }

    @Override
    public ComponentModelConfig getModelConfigByFileType(String fileType) {
        ComponentModelLoader loader = findByFileType(fileTypeLoaders, fileType);
        return loader == null ? null : modelTypeConfigs.get(loader.getModelType());
    }

    @Override
    public ComponentModelLoader getComponentModelLoader(String fileType) {
        ComponentModelLoader loader = findByFileType(fileTypeLoaders, fileType);
        return loader;
    }

    private ComponentModelConfig requireModelConfigByModelPath(String path) {
        ComponentModelConfig config = getModelConfigByModelPath(path);
        if (config == null)
            throw new NopException(ERR_COMPONENT_UNKNOWN_MODEL_FILE_TYPE)
                    .param(ARG_FILE_TYPE, StringHelper.fileType(path)).param(ARG_RESOURCE_PATH, path);
        return config;
    }

    private IResourceObjectLoader<? extends IComponentModel> requireLoader(String modelType,
                                                                           String fileType) {
        ComponentModelLoader loader = fileTypeLoaders.get(fileType);
        if (loader == null) {
            int pos = fileType.lastIndexOf('.');
            if (pos > 0) {
                fileType = fileType.substring(pos + 1);
                loader = fileTypeLoaders.get(fileType);
            }
        }
        if (loader == null || !loader.getModelType().equals(modelType))
            throw new NopException(ERR_COMPONENT_UNKNOWN_FILE_TYPE_FOR_MODEL_TYPE).param(ARG_FILE_TYPE, fileType)
                    .param(ARG_MODEL_TYPE, modelType);
        return loader.getLoader();
    }

    private <T> T findByFileType(Map<String, T> map, String fileType) {
        T ret = map.get(fileType);
        if (ret == null) {
            int pos = fileType.indexOf('.');
            if (pos > 0) {
                ret = map.get(fileType.substring(pos + 1));
            }
        }
        return ret;
    }

    @Override
    public String buildComponentPath(String modelPath, String genFormat) {
        ComponentModelConfig config = requireModelConfigByModelPath(modelPath);

        if (config.getGenPathStrategy() == null) {
            throw new NopException(ERR_COMPONENT_NO_GEN_PATH_STRATEGY).param(ARG_RESOURCE_PATH, modelPath)
                    .param(ARG_MODEL_TYPE, config.getModelType());
        }

        return config.getGenPathStrategy().buildComponentPath(modelPath, genFormat);
    }

    @Override
    public <T> T loadPrecompiledObject(String resourcePath) {
        String cpFile = ResourceHelper.getCpPath(resourcePath);
        IResource resource = VirtualFileSystem.instance().getResource(cpFile);
        if (!resource.exists())
            return null;
        return (T) ResourceHelper.readObject(resource);
    }

    @Override
    public IResourceReference resolveResource(String resourcePath) {
        IResourceReference resource = changeChecker.resolveResource(resourcePath);
        if (resource == null)
            resource = new UnknownResource(resourcePath);
        return resource;
    }

    @Override
    public <T> T collectDepends(String resourcePath, Supplier<T> task) {
        return dependsManager.collectDepends(resourcePath, task);
    }

    @Override
    public <T> T collectDependsTo(ResourceDependencySet dep, Supplier<T> task) {
        return dependsManager.collectDependsTo(dep, task);
    }

    @Override
    public <T> T ignoreDepends(Supplier<T> task) {
        return dependsManager.ignoreDepends(task);
    }

    @Override
    public void traceDepends(String depResourcePath) {
        dependsManager.traceDepends(depResourcePath);
    }

    @Override
    public void traceAllDepends(Collection<ResourceDependencySet> depends) {
        dependsManager.traceAllDepends(depends);
    }

    public ResourceDependencySet getResourceDepends(String resourcePath) {
        return dependsManager.getResourceDepends(resourcePath);
    }

    public ResourceDependencySet getModelDepends(String resourcePath) {
        String modelType = findModelTypeFromPath(resourcePath);
        IResourceLoadingCache<ComponentCacheEntry> cache = makeModelCache(modelType, resourcePath);
        return cache.getResourceDependsSet(resourcePath);
    }

    @Override
    public String dumpDependsSet(ResourceDependencySet deps) {
        String info = ResourceDependsHelper.dumpDependsSet(deps);
        return info;
    }

    @Override
    public <T> T runWhenDependsChanged(String path, Supplier<T> loader) {
        return dependsManager.runWhenDependsChanged(path, loader);
    }
}