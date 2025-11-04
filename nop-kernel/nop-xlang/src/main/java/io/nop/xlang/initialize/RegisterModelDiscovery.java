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
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
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

    public void registerAll(ICancellable cancellable) {
        IResource registryResource = VirtualFileSystem.instance().getResource(CoreConstants.MAIN_REGISTRY_PATH);
        XNode registryNode;
        if (registryResource.exists()) {
            registryNode = XModelInclude.instance().loadActiveNodeFromResource(registryResource);
        } else {
            registryNode = discover();
        }

        dumpModel(registryNode);

        Object model = new DslModelParser(XLangConstants.XDSL_SCHEMA_REGISTRY).parseFromNode(registryNode);
        processModel(model, cancellable);
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
//                if (AppConfig.isDebugMode()) {
//                    existing.dump("merge-register-model");
//                }
            }
        }
        return node;
    }
//
//    private void mergeChild(XNode nodeA, XNode nodeB, String childName) {
//        XNode childB = nodeB.childByTag(childName);
//        if (childB == null)
//            return;
//
//        XNode childA = nodeA.childByTag(childName);
//        if (childA == null) {
//            nodeA.appendChild(childB.detach());
//        } else {
//            childA.appendChildren(childB.detachChildren());
//        }
//    }

    private void processModel(Object registry, ICancellable cancellable) {
        Object models = BeanTool.getProperty(registry, "models");
        if (models != null) {
            List<Object> list = CollectionHelper.toList(models);
            for (Object model : list) {
                ComponentModelConfig config = buildConfig(model);
                ICancellable cleanup = ResourceComponentManager.instance().registerComponentModelConfig(config);
                if (cancellable != null) {
                    cancellable.appendOnCancelTask(cleanup::cancel);
                }
            }
        }
    }

    ComponentModelConfig buildConfig(Object model) {
        ComponentModelConfig config = new ComponentModelConfig();
        String name = (String) BeanTool.getProperty(model, "name");
        config.setModelType(name);

        boolean supportVersion = true;

        Object resolveHandler = BeanTool.getProperty(model, "resolveHandler");
        String resolveInDir = null;
        if (resolveHandler != null) {
            resolveInDir = (String) BeanTool.getProperty(resolveHandler, "resolveInDir");
            config.setResolveInDir(resolveInDir);
            config.setResolveDefaultLoader(buildDefaultResolveLoader(resolveHandler));
            supportVersion = ConvertHelper.toPrimitiveBoolean(
                    BeanTool.getProperty(resolveHandler, "supportVersion"), true, NopException::new);
        }
        config.setSupportVersion(supportVersion);

        List<Object> loaders = (List<Object>) BeanTool.getProperty(model, "loaders");
        if (loaders != null) {
            for (Object loader : loaders) {
                String type = (String) BeanTool.getProperty(loader, "type");
                String fileType = (String) BeanTool.getProperty(loader, "fileType");

                String impPath = null;
                String schemaPath = null;

                if ("xdsl-loader".equals(type)) {
                    schemaPath = (String) BeanTool.getProperty(loader, "schemaPath");
                    config.setXdefPath(schemaPath);
                    if (JsonTool.isJsonOrYamlFileExt(StringHelper.fileExtFromFileType(fileType))) {
                        config.loader(fileType, makeLoaderConfig(impPath, schemaPath, new DslJsonResourceLoader(schemaPath, resolveInDir)));
                    } else {
                        config.loader(fileType, makeLoaderConfig(impPath, schemaPath, new DslXmlResourceLoader(schemaPath, resolveInDir)));
                    }
                } else if ("xlsx-loader".equals(type)) {
                    impPath = (String) BeanTool.getProperty(loader, "impPath");
                    config.setImpPath(impPath);
                    if (DslModelHelper.supportExcelModelLoader()) {
                        config.loader(fileType, makeLoaderConfig(impPath, schemaPath,
                                (IResourceObjectLoader<? extends IComponentModel>) DslModelHelper.newExcelModelLoader(impPath)));
                    } else {
                        LOG.warn("nop.registry.ignore-xlsx-loader-since-no-xlsx-parser:fileType={},impPath={}",
                                fileType, impPath);
                    }
                }
            }

            for (Object loader : loaders) {
                String type = (String) BeanTool.getProperty(loader, "type");
                String fileType = (String) BeanTool.getProperty(loader, "fileType");
                boolean optional = ConvertHelper.toPrimitiveBoolean(BeanTool.getProperty(loader, "optional"),
                        false, NopException::new);
                if ("loader".equals(type)) {
                    initLoader(config, loader, fileType, optional);
                }
            }
        }

        List<Object> transformers = (List<Object>) BeanTool.getProperty(model, "transformers");
        if (transformers != null) {
            for (Object transformer : transformers) {
                initTransformer(config, transformer, name);
            }
        }

        return config;
    }

    private ComponentModelConfig.LoaderConfig makeLoaderConfig(String impPath, String xdefPath, IResourceObjectLoader<? extends IComponentModel> loader) {
        return new ComponentModelConfig.LoaderConfig(impPath, xdefPath, loader);
    }

    private void initLoader(ComponentModelConfig config, Object loader, String fileType, boolean optional) {
        String className = (String) BeanTool.getProperty(loader, "className");
        boolean returnXNode = ConvertHelper.toPrimitiveBoolean(BeanTool.getProperty(loader, "returnXNode"));

        try {
            IResourceObjectLoader loaderBean = newLoader(className);
            if (returnXNode)
                loaderBean = new XNodeToModelResourceObjectLoader(config.getXdefPath(), config.getResolveInDir(), loaderBean);
            config.loader(fileType, makeLoaderConfig(null, null, loaderBean));
        } catch (NoClassDefFoundError | NopException e) {
            if (!optional) {
                throw NopException.adapt(e);
            } else {
                LOG.warn("nop.register-model.ignore-invalid-loader:fileType={},className={}", fileType, className, e);
            }
        }
    }

    private void initTransformer(ComponentModelConfig config, Object transformer, String modelType) {
        String target = (String) BeanTool.getProperty(transformer, "target");
        String className = (String) BeanTool.getProperty(transformer, "className");
        boolean optional = ConvertHelper.toPrimitiveBoolean(BeanTool.getProperty(transformer, "optional"),
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

    IResourceObjectLoader buildDefaultResolveLoader(Object resolveHandler) {
        String beanName = (String) BeanTool.getProperty(resolveHandler, "defaultLoaderBean");
        if (!StringHelper.isEmpty(beanName)) {
            LOG.info("nop.use-default-loader-bean:beanName={}", beanName);
            return new IResourceObjectLoader() {
                @Override
                public Object loadObjectFromPath(String path) {
                    return ((IResourceObjectLoader) BeanContainer.instance().getBean(beanName)).loadObjectFromPath(path);
                }

                @Override
                public Object parseFromResource(IResource resource) {
                    return ((IResourceObjectLoader) BeanContainer.instance().getBean(beanName)).parseFromResource(resource);
                }
            };
        }
        String className = (String) BeanTool.getProperty(resolveHandler, "defaultLoaderClass");
        if (StringHelper.isEmpty(className))
            return null;

        return newLoader(className);
    }

    IComponentTransformer newTransformer(String className) {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        if (IComponentTransformer.class.isAssignableFrom(classModel.getRawClass()))
            return src -> ((IComponentTransformer) classModel.newInstance()).transform(src);

        IFunctionModel fn = classModel.getMethod("transform", 1);
        Guard.notNull(fn, className + ".transform not exists");
        return src -> (IComponentModel) fn.call1(classModel.newInstance(), src, DisabledEvalScope.INSTANCE);
    }

    IResourceObjectLoader newLoader(String className) {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        if (IResourceObjectLoader.class.isAssignableFrom(classModel.getRawClass())) {
            return new IResourceObjectLoader() {
                @Override
                public Object loadObjectFromPath(String path) {
                    return ((IResourceObjectLoader) classModel.newInstance()).loadObjectFromPath(path);
                }

                @Override
                public Object parseFromResource(IResource resource) {
                    return ((IResourceObjectLoader) classModel.newInstance()).parseFromResource(resource);
                }
            };
        }

        IFunctionModel fn = classModel.getMethod("parseFromVirtualPath", 1);
        Guard.notNull(fn, className + ".parseFromVirtualPath not exists");

        IFunctionModel parseFn = classModel.getMethod("parseFromResource", 1);
        return new IResourceObjectLoader() {
            @Override
            public Object loadObjectFromPath(String path) {
                return fn.call1(classModel.newInstance(), path, DisabledEvalScope.INSTANCE);
            }

            @Override
            public Object parseFromResource(IResource resource) {
                if (parseFn != null)
                    return parseFn.call1(classModel.newInstance(), resource, DisabledEvalScope.INSTANCE);
                return loadObjectFromPath(resource.getPath());
            }
        };
    }
}