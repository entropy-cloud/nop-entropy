/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.starter;

import io.nop.api.core.config.IConfigExecutor;
import io.nop.api.core.util.OrderedComparator;
import io.nop.commons.util.StringHelper;
import io.nop.config.ConfigConstants;
import io.nop.config.source.IConfigService;
import io.nop.config.source.IConfigSource;
import io.nop.config.source.IConfigSourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

public class SysServiceLoader {
    static final Logger LOG = LoggerFactory.getLogger(SysServiceLoader.class);

    public static IConfigService loadConfigService(IConfigSource configSource) {
        String impl = configSource.getConfigValue(ConfigConstants.CFG_CONFIG_SERVICE_IMPL, "");

        ServiceLoader<IConfigService> loader = ServiceLoader.load(IConfigService.class);
        if (StringHelper.isEmpty(impl)) {
            IConfigService service = first(loader);
            if (service != null) {
                LOG.debug("nop.config.use-config-service:{}", service.getName());
                return service;
            }
            LOG.debug("nop.config.not-find-config-service-impl");
            return null;
        } else {
            for (IConfigService service : loader) {
                if (service.getName().equals(impl)) {
                    LOG.info("nop.config.use-config-service:{}", service.getName());
                    return service;
                } else {
                    LOG.info("nop.config.ignore-config-service:{}", service.getName());
                }
            }
            LOG.warn("nop.config.service-impl-not-exists:{}", impl);
            return null;
        }
    }

    public static List<IConfigSourceLoader> getConfigSourceLoaders() {
        ServiceLoader<IConfigSourceLoader> loaders = ServiceLoader.load(IConfigSourceLoader.class);
        List<IConfigSourceLoader> ret = new ArrayList<>();
        for (IConfigSourceLoader loader : loaders) {
            if (loader.isEnabled()) {
                ret.add(loader);
                LOG.debug("nop.config.use-config-source-loader:{}", loader.getClass());
            } else {
                LOG.debug("nop.config.ignore-config-source-loader:{}", loader.getClass());
            }
        }
        ret.sort(OrderedComparator.instance());
        return ret;
    }

    public static IConfigExecutor loadConfigExecutor(IConfigSource configSource) {
        String impl = configSource.getConfigValue(ConfigConstants.CFG_CONFIG_EXECUTOR_IMPL, "");

        ServiceLoader<IConfigExecutor> loader = ServiceLoader.load(IConfigExecutor.class);
        if (StringHelper.isEmpty(impl)) {
            IConfigExecutor executor = first(loader);
            if (executor != null) {
                LOG.debug("nop.config.use-config-executor:{}", executor.getName());
                return executor;
            }
            LOG.debug("nop.config.not-find-config-executor-impl");
            return null;
        } else {
            for (IConfigExecutor executor : loader) {
                if (executor.getName().equals(impl)) {
                    LOG.info("nop.config.use-config-executor:{}", executor.getName());
                    return executor;
                }
            }
            LOG.warn("nop.config.executor-impl-not-exists:{}", impl);
            return null;
        }
    }

    static <T> T first(Iterable<T> c) {
        Iterator<T> it = c.iterator();
        if (it.hasNext())
            return it.next();
        return null;
    }
}
