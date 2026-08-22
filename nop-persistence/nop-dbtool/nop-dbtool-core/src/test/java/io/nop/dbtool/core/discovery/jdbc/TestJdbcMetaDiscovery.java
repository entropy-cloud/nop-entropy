/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core.discovery.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dbtool.core.DataBaseMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbcMetaDiscovery {

    private static HikariDataSource dataSource;

    private Connection connection;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(2);
        dataSource = ds;
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() throws SQLException {
        connection = dataSource.getConnection();
    }

    @AfterEach
    public void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void testDiscoverFillsProductMetaData() {
        // The reverse-engineered metadata must expose the JDBC driver/product
        // information instead of always staying null
        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forConnection(connection);
        DataBaseMeta meta = discovery.discover(null, null, "%");

        assertEquals("H2", meta.getProductName());
        assertNotNull(meta.getProductVersion(), "productVersion must be read from DatabaseMetaData");
        assertNotNull(meta.getDriverName(), "driverName must be read from DatabaseMetaData");
        assertNotNull(meta.getDriverVersion(), "driverVersion must be read from DatabaseMetaData");
    }

    @Test
    public void testGetCatalogsClosesResultSet() {
        assertResultSetsClosed("getCatalogs");
    }

    @Test
    public void testGetSchemasClosesResultSet() {
        assertResultSetsClosed("getSchemas");
    }

    /**
     * getCatalogs/getSchemas run on a caller-managed connection (forConnection
     * mode), so their ResultSets must be closed by the method itself instead
     * of relying on connection close.
     */
    private void assertResultSetsClosed(String methodName) {
        AtomicBoolean closed = new AtomicBoolean(false);
        Connection tracking = trackingConnection(connection, methodName, closed);

        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forConnection(tracking);
        List<String> result = methodName.equals("getCatalogs")
            ? discovery.getCatalogs() : discovery.getSchemas();

        assertFalse(result.isEmpty(), methodName + " should return at least one entry on H2");
        assertTrue(closed.get(), methodName + " must close its ResultSet");
    }

    private Connection trackingConnection(Connection delegate, String trackedMethod, AtomicBoolean closed) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("getMetaData")) {
                DatabaseMetaData metaData = (DatabaseMetaData) method.invoke(delegate, args);
                return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                    new Class<?>[]{DatabaseMetaData.class},
                    (metaProxy, metaMethod, metaArgs) -> {
                        Object result = invoke(metaMethod, metaData, metaArgs);
                        if (metaMethod.getName().equals(trackedMethod) && result instanceof ResultSet) {
                            return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                                new Class<?>[]{ResultSet.class},
                                (rsProxy, rsMethod, rsArgs) -> {
                                    if (rsMethod.getName().equals("close")) {
                                        closed.set(true);
                                    }
                                    return invoke(rsMethod, result, rsArgs);
                                });
                        }
                        return result;
                    });
            }
            return invoke(method, delegate, args);
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, handler);
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
