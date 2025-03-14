/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.config.ConfigConstants;
import io.nop.config.source.DynamicTextConfigSourceLoader;
import io.nop.config.source.IConfigService;
import io.nop.config.source.IConfigSource;
import io.nop.config.source.IDynamicTextConfigLoader;
import io.nop.config.source.IUpdatableConfigService;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.Executor;

import static io.nop.cluster.nacos.NacosConfigConstants.CFG_CONFIG_NACOS_ENABLED;
import static io.nop.cluster.nacos.NacosConfigConstants.CFG_NACOS_GROUP;
import static io.nop.cluster.nacos.NacosConfigConstants.CFG_NACOS_NAMESPACE;
import static io.nop.cluster.nacos.NacosConfigConstants.CFG_NACOS_PASSWORD;
import static io.nop.cluster.nacos.NacosConfigConstants.CFG_NACOS_SERVER_ADDR;
import static io.nop.cluster.nacos.NacosConfigConstants.CFG_NACOS_TIMEOUT;
import static io.nop.cluster.nacos.NacosConfigConstants.CFG_NACOS_USERNAME;
import static io.nop.config.ConfigConstants.FILE_POSTFIX_PROPERTIES;
import static io.nop.config.ConfigConstants.FILE_POSTFIX_YAML;
import static io.nop.config.ConfigConstants.FILE_POSTFIX_YML;

public class NacosConfigService implements IConfigService, IDynamicTextConfigLoader, IUpdatableConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(NacosConfigService.class);

    private ConfigService configService;
    private String group;
    private long timeout = 5000;

    private boolean enabled;

    @Override
    public boolean isEnabled() {
        boolean enabled = ConvertHelper.toPrimitiveBoolean(AppConfig.var(CFG_CONFIG_NACOS_ENABLED, true));
        return enabled;
    }

    @Override
    public void start() {
        Properties props = buildProps();
        group = AppConfig.var(CFG_NACOS_GROUP, "public");
        Long value = ConvertHelper.toLong(AppConfig.var(CFG_NACOS_TIMEOUT));
        if (value != null) {
            this.timeout = value;
        }

        try {
            configService = NacosFactory.createConfigService(props);
        } catch (NacosException e) {
            throw newError(e);
        }
    }

    protected Properties buildProps() {
        Properties props = loadConfigProperties();

        if (!props.containsKey(PropertyKeyConst.NAMESPACE)) {
            String namespace = AppConfig.var(CFG_NACOS_NAMESPACE, "DEFAULT");
            props.put(PropertyKeyConst.NAMESPACE, namespace);
        }

        if (!props.containsKey(PropertyKeyConst.SERVER_ADDR)) {
            String addr = AppConfig.var(CFG_NACOS_SERVER_ADDR, "localhost");
            props.put(PropertyKeyConst.SERVER_ADDR, addr);
        }

        if (!props.containsKey(PropertyKeyConst.USERNAME)) {
            String userName = AppConfig.var(CFG_NACOS_USERNAME, "");
            if (!StringHelper.isEmpty(userName))
                props.put(PropertyKeyConst.USERNAME, userName);
        }

        if (!props.containsKey(PropertyKeyConst.PASSWORD)) {
            String password = AppConfig.var(CFG_NACOS_PASSWORD, "");
            if (!StringHelper.isEmpty(password))
                props.put(PropertyKeyConst.PASSWORD, password);
        }

        return props;
    }

    private Properties loadConfigProperties() {
        String file = AppConfig.var(ConfigConstants.CFG_CONFIG_SERVICE_PROPERTIES_LOCATION,
                ConfigConstants.CFG_PATH_CONFIG_SERVICE_PROPERTIES);

        IResource resource = buildConfigResource(file);
        if (!resource.exists())
            return new Properties();
        return ResourceHelper.readProperties(resource);
    }

    protected IResource buildConfigResource(String path) {
        IResource resource;
        if (ResourceHelper.startsWithNamespace(path, ResourceConstants.RESOURCE_NS_CLASSPATH)) {
            resource = new ClassPathResource(path);
        } else {
            resource = new FileResource(new File(path));
        }
        return resource;
    }

    protected NopException newError(NacosException e) {
        throw NopException.adapt(e);
    }

    @Override
    public void stop() {
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (Exception e) {
                LOG.error("nop.err.config.nacos.shutdown-config-service-fail", e);
            }
            configService = null;
        }
    }

    @Override
    public ConfigInfo getConfigInfo(IConfigSource baseSource, String dataId, IConfigUpdateListener listener) {
        Guard.notNull(listener, "listener is null");

        Listener nacosListener = new Listener() {
            @Override
            public Executor getExecutor() {
                return command -> command.run();
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                listener.onUpdateConfig(configInfo);
            }
        };

        try {
            String content = configService.getConfigAndSignListener(dataId, group, timeout, nacosListener);
            return new ConfigInfo(content, () -> {
                if (configService != null)
                    configService.removeListener(dataId, group, nacosListener);
            });
        } catch (NacosException e) {
            throw newError(e);
        }
    }

    @Override
    public IConfigSource getConfigSource(IConfigSource baseSource, String dataId) {
        return new DynamicTextConfigSourceLoader(this, dataId).loadConfigSource(baseSource);
    }

    @Override
    public void publishConfig(String dataId, String group, String content) {
        ConfigType type = getConfigType(dataId);
        try {
            configService.publishConfig(dataId, group, content, type.getType());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private ConfigType getConfigType(String dataId) {
        if (dataId.endsWith(FILE_POSTFIX_YML) || dataId.endsWith(FILE_POSTFIX_YAML)) {
            return ConfigType.YAML;
        }
        if (dataId.endsWith(FILE_POSTFIX_PROPERTIES))
            return ConfigType.PROPERTIES;
        return ConfigType.TEXT;
    }
}
