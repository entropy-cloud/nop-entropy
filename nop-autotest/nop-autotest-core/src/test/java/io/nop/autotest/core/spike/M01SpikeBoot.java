package io.nop.autotest.core.spike;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.DaoConfigs;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConfigs;

/**
 * M0.1 fixture-bundle spike bootstrap (nop-app-erp plan 2026-10-01-1853-1).
 * <p>
 * Replicates the container bootstrap performed by NopJunitExtension.beforeAll +
 * AutoTestCase.initBeans/initDao, WITHOUT extending AutoTestCase — proving that a
 * standalone recording session can obtain the container-level shared
 * IOrmSessionFactory and register/unregister its own AutoTestOrmHook instance
 * without any AutoTestCaseData (caseData) dependency.
 */
public class M01SpikeBoot {

    public static void start() {
        AppConfig.getConfigProvider().reset();
        BaseTestCase.beginTest();

        BaseTestCase.setTestConfig(IocSpikeConfigs.APP_BEANS_CONTAINER_START_MODE, BeanContainerStartMode.ALL_LAZY.name());
        // M1.3 mechanism v4: explicitly load the spike beans override (its file name does
        // not match the app*.beans.xml scan; without this line the file is a silent no-op)
        BaseTestCase.setTestConfig(IocSpikeConfigs.APP_BEANS_FILES, "/nop/spike/beans/m01-fixture-spike.beans.xml");
        BaseTestCase.setTestConfig(OrmConfigs.CFG_INIT_DATABASE_SCHEMA, true);
        BaseTestCase.setTestConfig(DaoConfigs.CFG_DATASOURCE_DRIVER_CLASS_NAME, "org.h2.Driver");
        BaseTestCase.setTestConfig(DaoConfigs.CFG_DATASOURCE_USERNAME, "sa");
        BaseTestCase.setTestConfig(DaoConfigs.CFG_DATASOURCE_PASSWORD, "");
        BaseTestCase.setTestConfig(DaoConfigs.CFG_DATASOURCE_JDBC_URL, "jdbc:h2:mem:" + StringHelper.generateUUID());

        CoreInitialization.initialize();
        BeanContainer.instance().restart();
    }

    public static void stop() {
        // hygiene (approval note): clear the explicit beans-file config so it cannot leak
        // into any same-JVM container bootstrapping after this boot
        BaseTestCase.clearTestConfig(IocSpikeConfigs.APP_BEANS_FILES.getName());
        CoreInitialization.destroy();
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
        BaseTestCase.endTest();
    }

    public static IOrmTemplate orm() {
        return (IOrmTemplate) BeanContainer.tryGetBean("nopOrmTemplate");
    }

    public static IDaoProvider daoProvider() {
        return (IDaoProvider) BeanContainer.tryGetBean("nopDaoProvider");
    }

    public static IJdbcTemplate jdbc() {
        return (IJdbcTemplate) BeanContainer.tryGetBean("nopJdbcTemplate");
    }
}
