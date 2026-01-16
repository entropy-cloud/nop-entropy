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
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.core.AutoTestConfigs;
import io.nop.autotest.core.util.TestClock;
import io.nop.commons.util.StringHelper;
import io.nop.config.ConfigConstants;
import io.nop.config.source.IConfigSource;
import io.nop.config.source.ResourceConfigSourceLoader;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.dao.DaoConfigs;

import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_FILES;
import static io.nop.ioc.IocConfigs.CFG_IOC_ENABLED;
import static io.nop.orm.OrmConfigs.CFG_INIT_DATABASE_SCHEMA;

public class NopTestConfigProcessor {

    public void process(NopTestConfig config) {
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

        if (config.forceSaveOutput()) {
            setTestConfig(AutoTestConfigs.CFG_AUTOTEST_FORCE_SAVE_OUTPUT, true);
        }

        if (!config.testConfigFile().isEmpty()) {
            ClassPathResource resource = new ClassPathResource(config.testConfigFile());

            IConfigSource configSource = new ResourceConfigSourceLoader(resource).loadConfigSource(null);
            configSource.getConfigValues().forEach((name, vl) -> {
                setTestConfig(name, vl.getValue());
            });
        }

        if (!config.testBeansFile().isEmpty()) {
            setTestConfig(CFG_IOC_APP_BEANS_FILES, config.testBeansFile());
        }

        // OptionalBoolean 类型需要判断是否设置了值
        if (config.initDatabaseSchema() != OptionalBoolean.NOT_SET) {
            setTestConfig(CFG_INIT_DATABASE_SCHEMA, config.initDatabaseSchema() == OptionalBoolean.TRUE);
        }

        if (config.enableActionAuth() != OptionalBoolean.NOT_SET) {
            setTestConfig(ApiConfigs.CFG_AUTH_ENABLE_ACTION_AUTH, config.enableActionAuth() == OptionalBoolean.TRUE);
        }

        if (config.enableDataAuth() != OptionalBoolean.NOT_SET) {
            setTestConfig(ApiConfigs.CFG_AUTH_ENABLE_DATA_AUTH, config.enableDataAuth() == OptionalBoolean.TRUE);
        }

        if (config.enableConfig() != OptionalBoolean.NOT_SET) {
            setTestConfig(ConfigConstants.CFG_CONFIG_ENABLED, config.enableConfig() == OptionalBoolean.TRUE);
        }

        if (config.enableIoc() != OptionalBoolean.NOT_SET) {
            setTestConfig(CFG_IOC_ENABLED, config.enableIoc() == OptionalBoolean.TRUE);
        }
    }
}
