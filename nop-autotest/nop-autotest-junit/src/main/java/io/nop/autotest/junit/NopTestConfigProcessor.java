/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.junit;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.core.util.TestClock;
import io.nop.commons.util.StringHelper;
import io.nop.config.source.IConfigSource;
import io.nop.config.source.ResourceConfigSourceLoader;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.dao.DaoConfigs;

import static io.nop.autotest.core.AutoTestConfigs.CFG_AUTOTEST_DISABLE_SNAPSHOT;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILES;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILE_ENABLED;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILE_PATTERN;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILE_SKIP_PATTERN;
import static io.nop.ioc.IocConfigs.CFG_IOC_AUTO_CONFIG_ENABLED;
import static io.nop.ioc.IocConfigs.CFG_IOC_AUTO_CONFIG_PATTERN;
import static io.nop.ioc.IocConfigs.CFG_IOC_AUTO_CONFIG_SKIP_PATTERN;
import static io.nop.ioc.IocConfigs.CFG_IOC_MERGED_BEANS_FILE_ENABLED;
import static io.nop.orm.OrmConfigs.CFG_INIT_DATABASE_SCHEMA;

public class NopTestConfigProcessor {

    public void process(NopTestConfig config) {
        if (config.debug()) {
            setTestConfig(ApiConfigs.CFG_DEBUG, true);
        }
        if (config.useTestClock()) {
            CoreMetrics.registerClock(new TestClock());
        }
        if (config.localDb()) {
            setTestConfig(DaoConfigs.CFG_DATASOURCE_DRIVER_CLASS_NAME, "org.h2.Driver");
            setTestConfig(DaoConfigs.CFG_DATASOURCE_USERNAME, "sa");
            setTestConfig(DaoConfigs.CFG_DATASOURCE_PASSWORD, "");
            // Note：在 Linux 中 H2 默认是大写模式，在单元测试中直接配置启用大小写无关，以确保兼容性
            setTestConfig(DaoConfigs.CFG_DATASOURCE_JDBC_URL, "jdbc:h2:mem:" + StringHelper.generateUUID() + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        }

        if (config.disableSnapshot()) {
            setTestConfig(CFG_AUTOTEST_DISABLE_SNAPSHOT, true);
        }

        setTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_MODE, config.beanContainerStartMode().name());

        if (!config.testConfigFile().isEmpty()) {
            ClassPathResource resource = new ClassPathResource(config.testConfigFile());

            IConfigSource configSource = new ResourceConfigSourceLoader(resource).loadConfigSource(null);
            configSource.getConfigValues().forEach((name, vl) -> {
                setTestConfig(name, vl.getValue());
            });
        }

        setTestConfig(CFG_IOC_AUTO_CONFIG_ENABLED, config.enableAutoConfig());
        if (!config.autoConfigPattern().isEmpty()) {
            setTestConfig(CFG_IOC_AUTO_CONFIG_PATTERN, config.autoConfigPattern());
        }
        if (!config.autoConfigSkipPattern().isEmpty()) {
            setTestConfig(CFG_IOC_AUTO_CONFIG_SKIP_PATTERN, config.autoConfigSkipPattern());
        }

        setTestConfig(CFG_IOC_MERGED_BEANS_FILE_ENABLED, config.enableMergedBeansFile());

        setTestConfig(CFG_IOC_APP_BEANS_FILE_ENABLED, config.enableAppBeansFile());
        if (!config.appBeansFilePattern().isEmpty()) {
            setTestConfig(CFG_IOC_APP_BEANS_FILE_PATTERN, config.appBeansFilePattern());
        }
        if (!config.appBeansFileSkipPattern().isEmpty()) {
            setTestConfig(CFG_IOC_APP_BEANS_FILE_SKIP_PATTERN, config.appBeansFileSkipPattern());
        }

        if (!config.testBeansFile().isEmpty()) {
            setTestConfig(CFG_IOC_APP_BEANS_FILES, config.testBeansFile());
        }

        setTestConfig(CFG_INIT_DATABASE_SCHEMA, config.initDatabaseSchema());

        if (!config.enableActionAuth().isEmpty()) {
            setTestConfig(ApiConfigs.CFG_AUTH_ENABLE_ACTION_AUTH, ConvertHelper.toBoolean(config.enableActionAuth()));
        }

        if (!config.enableDataAuth().isEmpty()) {
            setTestConfig(ApiConfigs.CFG_AUTH_ENABLE_DATA_AUTH, ConvertHelper.toBoolean(config.enableDataAuth()));
        }
    }
}
