/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.initialize;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceObjectLoaderFactory;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.IComponentTransformer;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ERR_XDSL_MODEL_NO_NAME_ATTR;

public class RegisterModelDiscovery {
    static final Logger LOG = LoggerFactory.getLogger(RegisterModelDiscovery.class);

    static RegisterModelDiscovery _instance = new RegisterModelDiscovery();

    public static RegisterModelDiscovery instance() {
        return _instance;
    }

    public static void registerInstance(RegisterModelDiscovery instance) {
        _instance = instance;
    }

    public void registerAll(ICancellable cancellable) {
        IResource registryResource = VirtualFileSystem.instance().getResource(CoreConstants.MAIN_REGISTRY_PATH);
        XNode registryNode;
        if (registryResource.exists()) {
            registryNode = XModelInclude.instance().loadActiveNodeFromResource(registryResource);
        } else {
            registryNode = discover();
        }

        dumpModel(registryNode);

        DynamicObject model = (DynamicObject) new DslModelParser(XLangConstants.XDSL_SCHEMA_REGISTRY)
                .parseFromNode(registryNode);
        processModel(model.toJson(), cancellable);
    }

    private void dumpModel(XNode node) {
        node.dump("register-model");
        if (AppConfig.isDebugMode()) {
            String dumpPath = ResourceHelper.getDumpPath(CoreConstants.MAIN_REGISTRY_PATH);

            IResource resource = VirtualFileSystem.instance().getResource(dumpPath);
            ResourceHelper.writeText(resource, node.fullXml(true, true));
        }
    }

    private XNode discover() {
        Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources(CoreConstants.CORE_REGISTRY_PATH,
                '.' + CoreConstants.FILE_TYPE_REGISTER_MODEL);

        XNode node = XNode.make("registry");
        node.setLocation(SourceLocation.fromPath(CoreConstants.MAIN_REGISTRY_PATH));

        node.setAttr(XDslKeys.DEFAULT.SCHEMA, XLangConstants.XDSL_SCHEMA_REGISTRY);
        node.setAttr("xmlns:x", XLangConstants.XDSL_SCHEMA_XDSL);

        XNode models = node.makeChild("models");
        Map<String, XNode> modelMap = new HashMap<>();

        IXDefinition xdef = SchemaLoader.loadXDefinition(XDslConstants.XDSL_SCHEMA_REGISTER_MODEL);

        for (IResource resource : resources) {
            XNode modelNode = XModelInclude.instance().loadActiveNodeFromResource(resource);
            modelNode.removeAttr("xmlns:x");

            String name = modelNode.attrText("name");
            if (StringHelper.isEmpty(name))
                throw new NopException(ERR_XDSL_MODEL_NO_NAME_ATTR)
                        .source(modelNode);

            XNode existing = modelMap.get(name);
            if (existing == null) {
                modelMap.put(name, modelNode);
                models.appendChild(modelNode);
            } else {
                new DeltaMerger(XDslKeys.DEFAULT).merge(existing, modelNode, xdef.getRootNode(), false);
            }
        }
        return node;
    }

    private void processModel(Map<String, Object> registry, ICancellable cancellable) {
        List<Map<String, Object>> models = (List<Map<String, Object>>) registry.get("models");
        if (models != null) {
            for (Map<String, Object> model : models) {
                ComponentModelConfig config = buildConfig(model);
                ICancellable cleanup = ResourceComponentManager.instance().registerComponentModelConfig(config);
                if (cancellable != null) {
                    cancellable.appendOnCancelTask(cleanup::cancel);
                }
            }
        }
    }

    ComponentModelConfig buildConfig(Map<String, Object> model) {
        ComponentModelConfig config = new ComponentModelConfig();
        String name = (String) model.get("name");
        String xdefPath = (String) model.get("xdefPath");
        config.setModelType(name);
        config.setXdefPath(xdefPath);

        boolean supportVersion = true;

        Map<String, Object> resolveHandler = (Map<String, Object>) model.get("resolveHandler");
        String resolveInDir = null;
        if (resolveHandler != null) {
            resolveInDir = (String) resolveHandler.get("resolveInDir");
            config.setResolveInDir(resolveInDir);
            config.setResolveDefaultLoader(buildDefaultResolveLoader(config, resolveHandler));
            supportVersion = ConvertHelper.toPrimitiveBoolean(resolveHandler.get("supportVersion"), true, NopException::new);
        }
        config.setSupportVersion(supportVersion);

        List<Map<String, Object>> loaders = (List<Map<String, Object>>) model.get("loaders");
        if (loaders != null) {
            for (Map<String, Object> loader : loaders) {
                String type = (String) loader.get("type");
                String fileType = (String) loader.get("fileType");

                String impPath = null;
                String schemaPath = null;

                if ("xdsl-loader".equals(type)) {
                    schemaPath = (String) loader.get("schemaPath");
                    if (config.getXdefPath() == null) {
                        config.setXdefPath(schemaPath);
                        config.setXdslFileType(fileType);
                    }

                    if (JsonTool.isJsonOrYamlFileExt(StringHelper.fileExtFromFileType(fileType))) {
                        config.loader(fileType, makeLoaderConfig(type, impPath, schemaPath, loader,
                                new DslJsonResourceLoader(schemaPath, resolveInDir)));
                    } else {
                        config.loader(fileType, makeLoaderConfig(type, impPath, schemaPath, loader,
                                new DslXmlResourceLoader(schemaPath, resolveInDir)));
                    }
                } else if ("xlsx-loader".equals(type)) {
                    impPath = (String) loader.get("impPath");
                    schemaPath = (String) loader.get("schemaPath");
                    if (config.getImpPath() == null)
                        config.setImpPath(impPath);

                    if (DslModelHelper.supportExcelModelLoader()) {
                        config.loader(fileType, makeLoaderConfig(type, impPath, schemaPath, loader,
                                DslModelHelper.newExcelModelLoader(impPath)));
                    } else {
                        LOG.warn("nop.registry.ignore-xlsx-loader-since-no-xlsx-parser:fileType={},impPath={}",
                                fileType, impPath);
                    }
                }
            }

            for (Map<String, Object> loader : loaders) {
                String type = (String) loader.get("type");
                String fileType = (String) loader.get("fileType");
                if ("loader".equals(type)) {
                    boolean optional = ConvertHelper.toPrimitiveBoolean(loader.get("optional"),
                            false, NopException::new);
                    initLoader(config, loader, type, fileType, optional);
                }
            }
        }

        List<Map<String, Object>> transformers = (List<Map<String, Object>>) BeanTool.getProperty(model, "transformers");
        if (transformers != null) {
            for (Map<String, Object> transformer : transformers) {
                initTransformer(config, transformer, name);
            }
        }

        return config;
    }

    IResourceObjectLoader<Object> buildDefaultResolveLoader(ComponentModelConfig config, Map<String, Object> resolveHandler) {
        String beanName = (String) resolveHandler.get("defaultLoaderBean");
        if (!StringHelper.isEmpty(beanName)) {
            LOG.info("nop.use-default-loader-bean:beanName={}", beanName);
            return new IResourceObjectLoader<Object>() {
                @Override
                public Object loadObjectFromPath(String path) {
                    return ((IResourceObjectLoader<Object>) BeanContainer.instance().getBean(beanName)).loadObjectFromPath(path);
                }

                @Override
                public Object loadObjectFromResource(IResource resource) {
                    return ((IResourceObjectLoader<Object>) BeanContainer.instance().getBean(beanName)).loadObjectFromResource(resource);
                }
            };
        }
        String className = (String) BeanTool.getProperty(resolveHandler, "defaultLoaderClass");
        if (StringHelper.isEmpty(className))
            return null;

        return newLoader(className, config, resolveHandler);
    }

    private ComponentModelConfig.LoaderConfig makeLoaderConfig(String type,
                                                               String impPath, String xdefPath,
                                                               Map<String, Object> attributes,
                                                               IResourceObjectLoader<Object> loader) {

        return new ComponentModelConfig.LoaderConfig(type, impPath, xdefPath, attributes, loader);
    }

    private void initLoader(ComponentModelConfig config, Map<String, Object> loader,
                            String type, String fileType, boolean optional) {
        String className = (String) loader.get("className");
        try {
            IResourceObjectLoader<Object> loaderBean = newLoader(className, config, loader);
            config.loader(fileType, makeLoaderConfig(type, null, null, loader, loaderBean));
        } catch (NoClassDefFoundError | NopException e) {
            if (!optional) {
                throw NopException.adapt(e);
            } else {
                LOG.warn("nop.register-model.ignore-invalid-loader:fileType={},className={}", fileType, className, e);
            }
        }
    }

    private void initTransformer(ComponentModelConfig config, Map<String, Object> transformer, String modelType) {
        String target = (String) transformer.get("target");
        String className = (String) transformer.get("className");
        boolean optional = ConvertHelper.toPrimitiveBoolean(transformer.get("optional"),
                false, NopException::new);

        try {
            config.transformer(target, newTransformer(className));
        } catch (NoClassDefFoundError | NopException e) {
            if (!optional) {
                throw NopException.adapt(e);
            } else {
                LOG.warn("nop.register-model.ignore-invalid-transformer:modelType={},className={}", modelType, className, e);
            }
        }
    }

    IComponentTransformer<Object, Object> newTransformer(String className) {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        if (IComponentTransformer.class.isAssignableFrom(classModel.getRawClass()))
            return (IComponentTransformer<Object, Object>) classModel.newInstance();

        throw new IllegalArgumentException("nop.err.core.invalid-transformer-type:" + className);
    }

    IResourceObjectLoader<Object> newLoader(String className, ComponentModelConfig config, Map<String, Object> attributes) {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        if (classModel.isAssignableTo(IResourceObjectLoaderFactory.class))
            return ((IResourceObjectLoaderFactory) classModel.newInstance()).newResourceObjectLoader(config, attributes);

        if (classModel.isAssignableTo(IResourceObjectLoader.class)) {
            return (IResourceObjectLoader) classModel.newInstance();
        }

        throw new IllegalArgumentException("nop.err.core.invalid-loader-type:" + className);
    }
}