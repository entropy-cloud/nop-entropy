/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.loader;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.IocConfigs;
import io.nop.ioc.IocConstants;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.impl.DefaultBeanClassIntrospection;
import io.nop.ioc.impl.IBeanClassIntrospection;
import io.nop.xlang.xdsl.XDslKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILES;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILE_ENABLED;
import static io.nop.ioc.IocConfigs.CFG_IOC_AUTO_CONFIG_ENABLED;
import static io.nop.ioc.IocConfigs.CFG_IOC_MERGED_BEANS_FILE_ENABLED;

public class AppBeanContainerLoader {
    static final Logger LOG = LoggerFactory.getLogger(AppBeanContainerLoader.class);

    private final IClassLoader classLoader;
    private final IBeanClassIntrospection introspection;

    public AppBeanContainerLoader(IClassLoader classLoader, IBeanClassIntrospection introspection) {
        this.classLoader = classLoader;
        this.introspection = introspection;
    }

    public AppBeanContainerLoader(IClassLoader classLoader) {
        this(classLoader, new DefaultBeanClassIntrospection(classLoader));
    }

    public AppBeanContainerLoader() {
        this(ClassHelper.getSafeClassLoader());
    }

    public IBeanContainerImplementor loadFromResource(String name, IResource resource, IBeanContainer parentContainer) {
        BeanContainerBuilder builder = new BeanContainerBuilder(classLoader, introspection, parentContainer);
        builder.addResource(resource);
        return builder.build(name);
    }

    public IBeanContainerImplementor loadFromResource(String name, IResource resource) {
        return loadFromResource(name, resource, null);
    }

    public IBeanContainerImplementor loadAppContainer(IBeanContainer parentContainer) {
        BeanContainerBuilder builder = new BeanContainerBuilder(classLoader, introspection, parentContainer);

        BeanContainerStartMode startMode = BeanContainerStartMode
                .fromText(CFG_IOC_APP_BEANS_CONTAINER_START_MODE.get());
        builder.startMode(startMode);
        if (startMode != null)
            LOG.info(CFG_IOC_APP_BEANS_CONTAINER_START_MODE.getName() + "=" + startMode);

        if (!loadMergedFile(builder))
            loadBeansFile(builder);

        IBeanContainerImplementor container = builder.build("app");

        dumpContainer(container);
        return container;
    }

    boolean loadMergedFile(IBeanContainerBuilder builder) {
        if (CFG_IOC_MERGED_BEANS_FILE_ENABLED.get()) {
            String mergedFile = getMergedAppBeansFile("app");
            IResource resource = VirtualFileSystem.instance().getResource(mergedFile);
            if (resource.exists()) {
                builder.addResource(resource);
                return true;
            }
        }
        return false;
    }

    void loadBeansFile(IBeanContainerBuilder builder) {
        if (CFG_IOC_AUTO_CONFIG_ENABLED.get()) {
            List<IResource> autoConfigResources = getAutoConfigResources();
            for (IResource resource : autoConfigResources) {
                try {
                    String text = ResourceHelper.readText(resource);
                    LOG.info("nop.ioc.use-auto-config:name={},config={}", resource.getName(), text);
                    text = text.replace(',', '\n');
                    List<String> files = StringHelper.stripedSplit(text, '\n');
                    for (String file : files) {
                        builder.addResource(VirtualFileSystem.instance().getResource(file));
                    }
                } catch (Exception e) {
                    LOG.error("nop.ioc.process-auto-config-fail:source={}", resource);
                    throw NopException.adapt(e);
                }
            }
        } else {
            LOG.info(CFG_IOC_AUTO_CONFIG_ENABLED.getName() + "=false");
        }

        if (CFG_IOC_APP_BEANS_FILE_ENABLED.get()) {
            ModuleManager.instance().getEnabledModules().forEach(module -> {
                List<? extends IResource> resources = getModuleAppResources(module.getModuleId());
                for (IResource resource : resources) {
                    builder.addResource(resource);
                }
            });

            List<? extends IResource> resources = getModuleAppResources("main");
            for (IResource resource : resources) {
                builder.addResource(resource);
            }
        } else {
            LOG.info(CFG_IOC_APP_BEANS_FILE_ENABLED.getName() + "=false");
        }

        String appBeansFiles = CFG_IOC_APP_BEANS_FILES.get();
        if (appBeansFiles != null) {
            for (String file : ConvertHelper.toCsvSet(appBeansFiles, NopException::new)) {
                builder.addResource(VirtualFileSystem.instance().getResource(file));
            }
        }
    }

    void dumpContainer(IBeanContainerImplementor container) {
        if (AppConfig.isDebugMode()) {
            String path = getMergedAppBeansFile(container.getId());

            String dumpPath = ResourceHelper.getDumpPath(path);
            XNode node = container.toConfigNode();
            XDslKeys keys = XDslKeys.of(node);
            node.setAttr(keys.VALIDATED, true);

            IResource resource = VirtualFileSystem.instance().getResource(dumpPath);
            ResourceHelper.writeText(resource, node.fullXml(true, true));
        }
    }

    String getMergedAppBeansFile(String containerId) {
        return "/nop/main/beans/merged-" + containerId + ".beans.xml";
    }

    List<IResource> getAutoConfigResources() {
        Predicate<String> filter = getAutoConfigFilter();

        Collection<? extends IResource> resources = VirtualFileSystem.instance()
                .getChildren(IocConstants.VFS_PATH_AUTOCONFIG);
        List<IResource> ret = new ArrayList<>(resources.size());
        for (IResource resource : resources) {
            if (resource.getName().endsWith(IocConstants.FILE_POSTFIX_BEANS)) {
                String name = StringHelper.removeFileExt(resource.getName());
                if (filter.test(name)) {
                    ret.add(resource);
                }
            }
        }
        return ret;
    }

    Predicate<String> getAutoConfigFilter() {
        Set<String> patterns = ConvertHelper.toCsvSet(IocConfigs.CFG_IOC_AUTO_CONFIG_PATTERN.get());
        Set<String> skipPatterns = ConvertHelper.toCsvSet(IocConfigs.CFG_IOC_AUTO_CONFIG_SKIP_PATTERN.get());

        if (CollectionHelper.isEmpty(patterns) && CollectionHelper.isEmpty(skipPatterns))
            return name -> true;

        if (patterns != null)
            LOG.info("nop.ioc.auto-config.pattern:{}", patterns);

        if (skipPatterns != null) {
            LOG.info("nop.ioc.auto-config.skip-pattern:{}", skipPatterns);
        }

        return name -> {
            if (skipPatterns != null) {
                for (String skipPattern : skipPatterns) {
                    if (StringHelper.matchSimplePattern(name, skipPattern))
                        return false;
                }
            }

            if (patterns != null) {
                for (String pattern : patterns) {
                    if (StringHelper.matchSimplePattern(name, pattern))
                        return true;
                }
                return false;
            }

            return true;
        };
    }

    Predicate<IResource> getAppBeansFilter() {
        Set<String> patterns = ConvertHelper.toCsvSet(IocConfigs.CFG_IOC_APP_BEANS_FILE_PATTERN.get());
        Set<String> skipPatterns = ConvertHelper.toCsvSet(IocConfigs.CFG_IOC_APP_BEANS_FILE_SKIP_PATTERN.get());

        if (CollectionHelper.isEmpty(patterns) && CollectionHelper.isEmpty(skipPatterns))
            return res -> true;

        if (patterns != null)
            LOG.info("nop.ioc.app-beans-file.pattern:{}", patterns);

        if (skipPatterns != null) {
            LOG.info("nop.ioc.app-beans-file.skip-pattern:{}", skipPatterns);
        }

        return res -> {
            String path = res.getPath();
            if (patterns != null) {
                for (String pattern : patterns) {
                    if (!StringHelper.matchSimplePattern(path, pattern))
                        return false;
                }
            }

            if (skipPatterns != null) {
                for (String skipPattern : skipPatterns) {
                    if (StringHelper.matchSimplePattern(path, skipPattern))
                        return false;
                }
            }
            return true;
        };
    }

    List<? extends IResource> getModuleAppResources(String moduleId) {
        String path = "/" + moduleId + "/beans";
        List<? extends IResource> resources = VirtualFileSystem.instance().getChildren(path);

        Predicate<IResource> filter = getAppBeansFilter();

        Iterator<? extends IResource> it = resources.iterator();
        while (it.hasNext()) {
            IResource resource = it.next();
            if (!isAppBeans(resource)) {
                it.remove();
            } else {
                if (!filter.test(resource)) {
                    LOG.info("nop.ioc.app-beans-file-is-ignored:resource={}", resource);
                    it.remove();
                }
            }
        }
        return resources;
    }

    boolean isAppBeans(IResource resource) {
        String name = resource.getName();
        if (name.equals("app.beans.xml"))
            return true;

        if (name.startsWith("app-") && name.endsWith(".beans.xml"))
            return true;

        return false;
    }
}