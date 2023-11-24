/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.starter;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigExecutor;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.LogLevel;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.config.ConfigConstants;
import io.nop.config.enhancer.DefaultConfigValueEnhancer;
import io.nop.config.enhancer.IConfigValueEnhancer;
import io.nop.config.impl.ConfigChangeApplier;
import io.nop.config.impl.DefaultConfigProvider;
import io.nop.config.source.CompositeConfigSource;
import io.nop.config.source.ConfigSourceHelper;
import io.nop.config.source.EnvConfigSourceLoader;
import io.nop.config.source.IConfigService;
import io.nop.config.source.IConfigSource;
import io.nop.config.source.IConfigSourceLoader;
import io.nop.config.source.ProfileConfigSource;
import io.nop.config.source.ResourceConfigSourceLoader;
import io.nop.config.source.RouterConfigSource;
import io.nop.config.source.StaticConfigSource;
import io.nop.config.source.SysPropertyConfigSourceLoader;
import io.nop.config.source.file.KeyFileConfigSource;
import io.nop.config.source.file.PropsFileConfigSource;
import io.nop.config.source.jdbc.JdbcConfig;
import io.nop.config.source.jdbc.JdbcConfigSource;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.module.ModuleManager;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.store.DefaultVirtualFileSystem;
import io.nop.log.core.LogConfigs;
import io.nop.log.core.LoggerConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.nop.config.ConfigConstants.CFG_KEY_FILE_CONFIG_SOURCE_PATHS;
import static io.nop.config.ConfigConstants.CFG_KEY_FILE_CONFIG_SOURCE_REFRESH_INTERVAL;
import static io.nop.config.ConfigConstants.CFG_PROPS_FILE_CONFIG_SOURCE_PATHS;
import static io.nop.config.ConfigConstants.CFG_PROPS_FILE_CONFIG_SOURCE_REFRESH_INTERVAL;
import static io.nop.config.ConfigConstants.DEFAULT_CONFIG_REFRESH_INTERVAL;
import static io.nop.config.ConfigErrors.ERR_CONFIG_MISSING_APPLICATION_NAME;

public class ConfigStarter extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(ConfigStarter.class);

    static final ConfigStarter g_instance = new ConfigStarter();

    public static ConfigStarter instance() {
        return g_instance;
    }

    // private IConfigSource baseSource;
    // private IConfigSource configSource;
    private IConfigService configService;

    private IConfigExecutor configExecutor;

    private DefaultConfigProvider configProvider;

    private ConfigChangeApplier changeApplier;

    private DefaultVirtualFileSystem vfs;

    private Cancellable cancellable = new Cancellable();

    private List<IConfigSource> closeableSources = new ArrayList<>();

    @Override
    protected void doStart() {
        // 1. 装载boostrap.yaml
        IConfigSource propsSource = new SysPropertyConfigSourceLoader().loadConfigSource(null);
        IConfigSource envSource = new EnvConfigSourceLoader().loadConfigSource(null);
        IConfigSource bootstrapSource = loadBootstrapSource(propsSource, envSource);

        // 优先级从高到低 System.properties --> bootstrap.yaml --> System.env
        CompositeConfigSource baseSource = new CompositeConfigSource(
                Arrays.asList(propsSource, bootstrapSource, envSource));

        initConfigProvider(baseSource);

        List<IConfigSource> configSources = new ArrayList<>();
        IConfigSource configSource;

        if (!CoreInitialization.isForAnalyze()) {
            // 2. 初始化配置中心服务
            initConfigService(baseSource);

            // 3. 初始化ConfigExecutor
            initConfigExecutor(baseSource);

            // 4. 从配置中心加载配置文件
            List<IConfigSource> serviceSources = loadFromConfigCenter(baseSource);
            closeableSources.addAll(serviceSources);

            // 5. 从k8s config map配置文件加载
            IConfigSource keyFileSource = loadKeyFileConfigSource(baseSource);
            closeableSources.add(keyFileSource);

            IConfigSource propsFileSource = loadPropsFileConfigSource(baseSource);
            closeableSources.add(propsFileSource);

            // 配置中心的优先级高于props和env
            configSources.addAll(serviceSources);
            if (keyFileSource != null) {
                configSources.add(keyFileSource);
            }
            if (propsFileSource != null) {
                configSources.add(propsFileSource);
            }
            configSources.addAll(baseSource.getConfigSources());

            configSource = new CompositeConfigSource(configSources);

            // 6. 获取数据库中的配置
            IConfigSource jdbcSource = loadJdbcSource(configSource);
            if (jdbcSource != null) {
                closeableSources.add(jdbcSource);

                configSources = new ArrayList<>();
                configSources.addAll(serviceSources);
                if (keyFileSource != null) {
                    configSources.add(keyFileSource);
                }
                if (propsFileSource != null) {
                    configSources.add(propsFileSource);
                }
                configSources.add(jdbcSource);
                configSources.addAll(baseSource.getConfigSources());

                configSource = new CompositeConfigSource(configSources);
            }

            // 7. 加载扩展配置
            List<IConfigSourceLoader> loaders = SysServiceLoader.getConfigSourceLoaders();
            for (IConfigSourceLoader loader : loaders) {
                IConfigSource source = loader.loadConfigSource(configSource);
                closeableSources.add(source);
                configSources.add(source);
            }
        } else {
            // 如果是分析模式，则忽略所有动态配置加载机制
            configSources.addAll(baseSource.getConfigSources());
        }

        configSource = new CompositeConfigSource(configSources);

        // 8. 装载应用配置 application.yaml
        List<IConfigSource> appSources = loadAppConfigs(configSource);

        configSources.addAll(appSources);

        List<String> profiles = getProfiles(baseSource);
        configSource = new CompositeConfigSource(configSources);
        if (!profiles.isEmpty()) {
            configSource = new ProfileConfigSource(profiles, configSource);
        }
        configSource = new RouterConfigSource(configSource);

        // 9. 初始化AppConfig
        updateConfigSource(configSource);

        configLogLevel();

        // 10. 初始化VirtualFileSystem
        initVirtualFileSystem();

        // 11. 加载ConfigModel，规范化配置项的数据类型
        loadConfigModel();

        loadI18nMessage();

        loadErrorCodeMappings();

        if (!CoreInitialization.isForAnalyze()) {
            // 允许配置更新
            if (changeApplier != null)
                this.changeApplier.activate();
        }
    }

    public void activate() {
        this.changeApplier.activate();
    }

    public void deactivate() {
        this.changeApplier.deactivate();
    }

    private IConfigSource loadBootstrapSource(IConfigSource propsSource, IConfigSource envSource) {
        String path = propsSource.getConfigValue(ConfigConstants.CFG_CONFIG_BOOTSTRAP_LOCATION, "");
        if (StringHelper.isEmpty(path)) {
            path = envSource.getConfigValue(ConfigConstants.CFG_CONFIG_BOOTSTRAP_LOCATION,
                    ConfigConstants.CFG_PATH_BOOTSTRAP_YAML);
        }
        IResource resource = buildConfigResource(path);
        if (!resource.exists()) {
            LOG.warn("nop.config.no-bootstrap-config-file:path{}", path);
            return new StaticConfigSource(path, Collections.emptyMap());
        }

        IConfigSourceLoader loader = new ResourceConfigSourceLoader(resource);
        return loader.loadConfigSource(null);
    }

    protected void initConfigService(IConfigSource baseSource) {
        boolean enabled = baseSource.getConfigValue(ConfigConstants.CFG_CONFIG_SERVICE_ENABLED, true);
        if (!enabled) {
            LOG.info("nop.config.config-service-is-disabled");
            return;
        }

        this.configService = SysServiceLoader.loadConfigService(baseSource);
        if (this.configService != null) {
            configService.start();
        }
    }

    protected void initConfigExecutor(IConfigSource baseSource) {
        this.configExecutor = SysServiceLoader.loadConfigExecutor(baseSource);
        if (this.configExecutor == null) {
            this.configExecutor = new SingleThreadConfigExecutor();
        }
        configExecutor.start();
    }

    protected IResource buildConfigResource(String path) {
        IResource resource;
        if (ResourceHelper.startsWithNamespace(path, ResourceConstants.CLASSPATH_NS)) {
            resource = new ClassPathResource(path);
        } else {
            resource = new FileResource(new File(path));
        }
        return resource;
    }

    protected List<String> getProfiles(IConfigSource baseSource) {
        String profile = baseSource.getConfigValue(ApiConfigs.CFG_PROFILE.getName(), "");

        String profileParent = baseSource.getConfigValue(ApiConfigs.CFG_PROFILE_PARENT.getName(), "");
        if (StringHelper.isEmpty(profile) && StringHelper.isEmpty(profileParent))
            return Collections.emptyList();

        List<String> profiles = new ArrayList<>();
        if (!StringHelper.isEmpty(profile)) {
            profiles.add(profile);
        }

        if (!StringHelper.isEmpty(profileParent))
            profiles.add(profileParent);
        return profiles;
    }

    protected List<IConfigSource> loadFromConfigCenter(IConfigSource baseSource) {
        if (configService == null)
            return Collections.emptyList();

        String productName = baseSource.getConfigValue(ConfigConstants.CFG_PRODUCT_NAME, "");

        String appName = baseSource.getConfigValue(ConfigConstants.CFG_APPLICATION_NAME, "");
        if (StringHelper.isEmpty(appName))
            throw new NopException(ERR_CONFIG_MISSING_APPLICATION_NAME);

        List<IConfigSource> serviceSources = new ArrayList<>(2);

        try {
            List<String> profiles = getProfiles(baseSource);
            for (String profile : profiles) {
                String dataId = appName + '-' + profile;
                IConfigSource profileSource = configService.getConfigSource(baseSource, dataId);
                serviceSources.add(profileSource);
            }

            if (!StringHelper.isEmpty(appName)) {
                String dataId = appName;
                IConfigSource serviceSource = configService.getConfigSource(baseSource, dataId);
                serviceSources.add(serviceSource);
            }

            if (!StringHelper.isEmpty(productName)) {
                String dataId = productName;
                IConfigSource serviceSource = configService.getConfigSource(baseSource, dataId);
                serviceSources.add(serviceSource);
            }
        } catch (Exception e) {
            IoHelper.safeCloseAll(serviceSources);
            throw NopException.adapt(e);
        }

        return serviceSources;
    }

    protected IConfigSource loadKeyFileConfigSource(IConfigSource configSource) {
        Collection<String> paths = ConvertHelper
                .toCsvSet(configSource.getConfigValue(CFG_KEY_FILE_CONFIG_SOURCE_PATHS, null), NopException::new);
        if (CollectionHelper.isEmpty(paths))
            return null;

        long refreshInterval = ConvertHelper.toLong(configSource.getConfigValue(
                CFG_KEY_FILE_CONFIG_SOURCE_REFRESH_INTERVAL, DEFAULT_CONFIG_REFRESH_INTERVAL), NopException::new);

        return new KeyFileConfigSource(paths, refreshInterval);
    }

    protected IConfigSource loadPropsFileConfigSource(IConfigSource configSource) {
        Collection<String> paths = ConvertHelper
                .toCsvSet(configSource.getConfigValue(CFG_PROPS_FILE_CONFIG_SOURCE_PATHS, null), NopException::new);
        if (CollectionHelper.isEmpty(paths))
            return null;

        long refreshInterval = ConvertHelper.toLong(configSource.getConfigValue(
                CFG_PROPS_FILE_CONFIG_SOURCE_REFRESH_INTERVAL, DEFAULT_CONFIG_REFRESH_INTERVAL), NopException::new);

        return new PropsFileConfigSource(paths, refreshInterval);
    }

    protected IConfigSource loadJdbcSource(IConfigSource configSource) {
        JdbcConfig config = new JdbcConfig();
        ConfigSourceHelper.initConfigBean(config, configSource, "nop.config.jdbc");
        if (!config.valid())
            return null;

        return new JdbcConfigSource(config);
    }

    protected List<IConfigSource> loadAppConfigs(IConfigSource configSource) {
        List<IConfigSource> appSources = new ArrayList<>(4);

        String additional = configSource.getConfigValue(ConfigConstants.CFG_CONFIG_ADDITIONAL_LOCATION, "");
        if (!StringHelper.isEmpty(additional)) {
            IResource resource = buildConfigResource(additional);
            IConfigSource source = new ResourceConfigSourceLoader(resource).loadConfigSource(configSource);
            appSources.add(source);
        }

        List<String> profiles = getProfiles(configSource);
        for (String profile : profiles) {
            IResource resource = getAppProfileFile(profile);
            if (resource.exists()) {
                IConfigSource source = new ResourceConfigSourceLoader(resource).loadConfigSource(configSource);
                appSources.add(source);
            }
        }

        String path = configSource.getConfigValue(ConfigConstants.CFG_CONFIG_LOCATION,
                ConfigConstants.CFG_PATH_APPLICATION_YAML);
        IResource resource = buildConfigResource(path);
        if (!resource.exists()) {
            IResource ymlResource = buildConfigResource(ConfigConstants.CFG_PATH_APPLICATION_YML);
            if (ymlResource.exists()) {
                resource = ymlResource;
            }
        }
        IConfigSource source = new ResourceConfigSourceLoader(resource).loadConfigSource(configSource);
        appSources.add(source);
        return appSources;
    }

    protected IResource getAppProfileFile(String profile) {
        String path = "classpath:application-" + profile + ".yaml";
        IResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            path = "classpath:application-" + profile + ".yml";
            resource = new ClassPathResource(path);
        }
        return resource;
    }

    protected void initConfigProvider(IConfigSource configSource) {
        // 通过getConfigReferences保留已经创建的ConfigReference对象
        IConfigValueEnhancer valueEnhancer = newValueEnhancer(configSource);
        this.configProvider = new DefaultConfigProvider(configSource, valueEnhancer,
                AppConfig.getConfigProvider().getConfigReferences());
        AppConfig.registerConfigProvider(configProvider);
    }

    protected IConfigValueEnhancer newValueEnhancer(IConfigSource configSource) {
        String className = configSource.getConfigValue(ConfigConstants.CFG_CONFIG_VALUE_ENHANCER_CLASS_NAME, null);
        Class<?> clazz = ClassHelper.loadClass(className, IConfigValueEnhancer.class);
        if (clazz != null) {
            ReflectionManager.instance().logReflectClass(clazz);
            return (IConfigValueEnhancer) ClassHelper.newInstance(clazz);
        }

        AESTextCipher cipher = new AESTextCipher();
        String encKey = configSource.getConfigValue(ConfigConstants.CFG_CONFIG_ENCRYPT_KEY, "");
        if (!encKey.isEmpty()) {
            cipher.setEncKey(encKey);
        }

        DefaultConfigValueEnhancer enhancer = new DefaultConfigValueEnhancer(cipher);
        return enhancer;
    }

    protected void updateConfigSource(IConfigSource configSource) {
//        traceConfigVars(configSource);

        configProvider.changeConfigSource(configSource);
        changeApplier = new ConfigChangeApplier(configExecutor, this::applyChange);
        configSource.addOnChange(changeApplier::requestUpdate);
    }

    protected void applyChange() {
        configProvider.applyChange();
    }

    protected void configLogLevel() {
        LogLevel logLevel = LogLevel.fromText(LogConfigs.CFG_LOG_LEVEL.get());
        if (!LoggerConfigurator.isInitialized() || logLevel == null)
            return;

        LoggerConfigurator.instance().changeRootLogLevel(logLevel);
        Runnable cleanup = configProvider.subscribeChange(LogConfigs.CFG_LOG_LEVEL.getName(),
                (provider, changeValues) -> {
                    LogLevel newLevel = LogLevel.fromText(LogConfigs.CFG_LOG_LEVEL.get());
                    LoggerConfigurator.instance().changeRootLogLevel(newLevel);
                });
        cancellable.appendOnCancelTask(cleanup);
    }

    protected void initVirtualFileSystem() {
        vfs = new DefaultVirtualFileSystem();
        VirtualFileSystem.registerInstance(vfs);
        ModuleManager.instance().discover();
    }

    protected void loadConfigModel() {
        configProvider.loadConfigModel();
    }

    protected void loadI18nMessage() {
        I18nMessageManager.instance().loadAllI18nMessages();
    }

    protected void loadErrorCodeMappings() {
        ErrorMessageManager.instance().loadErrorCodeMappings();
    }

    @Override
    protected void doStop() {
        cancellable.cancel();

        if (this.changeApplier != null) {
            this.changeApplier.deactivate();
        }

        if (configExecutor != null) {
            configExecutor.stop();
        }

        if (vfs != null)
            vfs.destroy();

        if (configService != null) {
            try {
                configService.stop();
            } catch (Exception e) {
                LOG.error("nop.config.stop-fail", e);
            }
        }
        IoHelper.safeCloseAll(closeableSources);
        closeableSources.clear();

        configService = null;
    }
}