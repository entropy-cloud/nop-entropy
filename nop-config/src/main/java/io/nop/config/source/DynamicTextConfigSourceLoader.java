/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.source;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.ConfigConstants;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static io.nop.config.ConfigConstants.CFG_PATH_PREFIX;

/**
 * 从配置中心加载
 */
public class DynamicTextConfigSourceLoader implements IConfigSourceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicTextConfigSourceLoader.class);

    private final IDynamicTextConfigLoader configService;
    private final String dataId;

    public DynamicTextConfigSourceLoader(IDynamicTextConfigLoader configService, String dataId) {
        this.configService = configService;
        this.dataId = dataId;
    }

    @Override
    public IConfigSource loadConfigSource(IConfigSource config) {
        DynamicConfigSource source = new DynamicConfigSource(dataId);

        IDynamicTextConfigLoader.ConfigInfo configInfo = configService.getConfigInfo(config, dataId, configContent -> {
            try {
                Map<String, ValueWithLocation> configVars = parseConfigVars(configContent);
                source.setConfigVars(configVars);
            } catch (Exception e) {
                LOG.error("nop.config.update-fail", e);
            }
        });

        try {
            Map<String, ValueWithLocation> configVars = parseConfigVars(configInfo.getContent());
            source.setOnClose(configInfo.getUnsubscribe());
            source.setConfigVars(configVars);
            return source;
        } catch (Exception e) {
            configInfo.getUnsubscribe().run();
            throw NopException.adapt(e);
        }
    }

    private String getResourcePath() {
        return "cfg:/" + dataId;
    }

    private Map<String, ValueWithLocation> parseConfigVars(String configInfo) {
        if (StringHelper.isEmpty(configInfo))
            return loadFromLocalFile();

        SourceLocation loc = SourceLocation.fromPath(getResourcePath());
        return loadFromConfigInfo(loc, configInfo);
    }

    private Map<String, ValueWithLocation> loadFromLocalFile() {
        IResource resource = new ClassPathResource(CFG_PATH_PREFIX + dataId);
        if (!resource.exists()) {
            return Collections.emptyMap();
        }
        String configInfo = ResourceHelper.readText(resource);
        return loadFromConfigInfo(SourceLocation.fromPath(resource.getPath()), configInfo);
    }

    private Map<String, ValueWithLocation> loadFromConfigInfo(SourceLocation loc, String configInfo) {
        if (StringHelper.isBlank(configInfo))
            return Collections.emptyMap();

        if (dataId.endsWith(ConfigConstants.FILE_POSTFIX_PROPERTIES)) {
            return loadFromProperties(loc, configInfo);
        } else {
            return loadFromJson(loc, configInfo);
        }
    }

    private Map<String, ValueWithLocation> loadFromProperties(SourceLocation loc, String configInfo) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(configInfo));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return ConfigSourceHelper.buildConfigValues(loc, props);
    }

    private Map<String, ValueWithLocation> loadFromJson(SourceLocation loc, String configInfo) {
        JsonParseOptions options = new JsonParseOptions();
        if (dataId.endsWith(ConfigConstants.FILE_POSTFIX_YAML) || dataId.endsWith(ResourceConstants.FILE_POSTFIX_YML)) {
            options.setYaml(true);
        }
        options.setKeepLocation(true);

        JObject obj = (JObject) JsonTool.instance().parseFromText(loc, configInfo, options);
        return ConfigSourceHelper.buildConfigValues(null, obj);
    }
}
