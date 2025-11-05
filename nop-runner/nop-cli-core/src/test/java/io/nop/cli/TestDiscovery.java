package io.nop.cli;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

@Disabled
public class TestDiscovery {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDiscovery2() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setUsername("LOADTEST_50");
        ds.setPassword("WLcn2025");
        //ds.setCatalog("LOADTEST_50");
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setUrl("jdbc:oracle:thin:@//192.168.101.38:8080/ORCLPDB");

        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forDataSource(ds);
        discovery.includeIndexes(false).includeRelations(false).includeUniqueKeys(false);
        List<String> catalogs = discovery.getCatalogs();
        System.out.println("catalogs=" + catalogs);

        List<String> schemas = discovery.getSchemas();
        System.out.println("schemas=" + schemas);

        DataBaseMeta meta = discovery.discover(null, "LOADTEST_50", "%");

        OrmModel model = meta.getOrmModel();
        DslModelHelper.dslModelToXNode(OrmConstants.XDSL_SCHEMA_ORM, model).dump();
    }
}
