/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.impl;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.config.IConfigBeanLoader;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;

import static io.nop.config.ConfigErrors.ARG_CONFIG_NAME;
import static io.nop.config.ConfigErrors.ERR_CONFIG_INVALID_CONFIG_BEAN_NAME;

public class DefaultConfigBeanLoader implements IConfigBeanLoader {
    static final Logger LOG = LoggerFactory.getLogger(DefaultConfigBeanLoader.class);

    private String basePath;

    @InjectValue("@cfg:nop.config.bean.base-path=/nop/cfg/beans")
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public <T> T loadConfigBeanForType(String configName, Type beanType, boolean ignoreUnknown) {
        if (configName == null || !StringHelper.isCanonicalFilePath(configName))
            throw new NopException(ERR_CONFIG_INVALID_CONFIG_BEAN_NAME).param(ARG_CONFIG_NAME, configName);

        IResource resource = findConfigResource(configName);
        if (ignoreUnknown) {
            if (!resource.exists()) {
                LOG.debug("nop.config.resource-file-not-exists:configName={},path={}", configName, resource.getPath());
                return null;
            }
        }

        return loadFromResource(resource, beanType);
    }

    protected <T> T loadFromResource(IResource resource, Type beanType) {
        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setRegistry(ValueResolverCompilerRegistry.DEFAULT);
        options.setExtendsGenerator(EvalExprProvider.getDeltaExtendsGenerator());
        options.setFeatureSwitchEvaluator(EvalExprProvider.getFeaturePredicateEvaluator());
        return JsonTool.loadDeltaBeanFromResource(resource, beanType, options);
    }

    private IResource findConfigResource(String configName) {
        String fileExt = StringHelper.fileExt(configName);
        String path = StringHelper.appendPath(basePath, configName);

        if (StringHelper.isEmpty(fileExt)) {
            String fullPath = StringHelper.appendPath(path, ResourceConstants.FILE_POSTFIX_YAML);
            IResource resource = VirtualFileSystem.instance().getResource(fullPath);
            if (!resource.exists()) {
                fullPath = StringHelper.appendPath(path, ResourceConstants.FILE_POSTFIX_JSON);
                resource = VirtualFileSystem.instance().getResource(fullPath);
            }
            return resource;
        }

        return VirtualFileSystem.instance().getResource(path);
    }
}