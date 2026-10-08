/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 (plan 2328): pins the production entry migration. The cluster launch entries
 * must load from the runtime module's MAIN classes (published jar), not test-classes —
 * if this test ever fails, the F-11 classpath limitation is back and the ops manual
 * warning block must be restored.
 *
 * <p>Driver assertions target {@link SharedJdbcInfrastructure#buildDataSource} only:
 * this module's surefire JVM runs with capped initialization (below DAO), so full
 * {@code SharedJdbcInfrastructure} construction — which probes a connection and drags
 * in dialect resolution — is covered by the gated multi-JVM E2Es instead. The Hikari
 * pool start itself fails fast on an unloadable driver class (Hikari semantics,
 * observed end-to-end during plan 2328 execution).
 */
public class TestLaunchMainScopeWiring {

    private static void assertMainScope(Class<?> clazz) {
        String path = clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class").toString();
        assertNotNull(path, clazz.getName() + " must be loadable");
        assertTrue(path.contains("/classes/"),
                clazz.getName() + " must load from main classes, got: " + path);
        assertFalse(path.contains("/test-classes/"),
                clazz.getName() + " must NOT load from test-classes, got: " + path);
    }

    @Test
    public void testLaunchEntriesAreMainScope() {
        assertMainScope(JobCoordinatorMain.class);
        assertMainScope(TaskManagerMain.class);
        assertMainScope(SharedJdbcInfrastructure.class);
        assertMainScope(PollingJdbcMessageService.class);
        // Supporting seam classes that were already main-scope stay pinned too.
        assertMainScope(ClusterLaunchConfig.class);
        assertMainScope(ClusterPipelineFactory.class);
    }

    @Test
    public void testJdbcDriverForwardedToHikariConfig() throws Exception {
        // A loadable-but-custom driver name is forwarded verbatim to the pool config.
        // Using a test-local Driver impl (loadable from the test classloader) keeps the
        // assertion distinguishable from the hardcoded-value failure mode: if
        // SharedJdbcInfrastructure ever stopped reading KEY_JDBC_DRIVER, the getter
        // would answer org.h2.Driver and this test would fail.
        ClusterLaunchConfig config = ClusterLaunchConfig.parse(new String[]{
                "jdbcUrl=jdbc:fake://wiring",
                "jdbcDriver=" + FakeDriver.class.getName()});
        try (HikariDataSource ds = SharedJdbcInfrastructure.buildDataSource(config)) {
            assertEquals(FakeDriver.class.getName(), ds.getDriverClassName());
        }

        // Unset key keeps the historical H2 default (zero behaviour change).
        ClusterLaunchConfig defaults = ClusterLaunchConfig.parse(new String[]{
                "jdbcUrl=jdbc:h2:mem:wiring-default"});
        try (HikariDataSource ds = SharedJdbcInfrastructure.buildDataSource(defaults)) {
            assertEquals("org.h2.Driver", ds.getDriverClassName());
        }
    }

    /**
     * Loadable {@link java.sql.Driver} used only as a configuration value — never
     * connected to (the pool is never started in this test).
     */
    public static class FakeDriver implements java.sql.Driver {
        @Override
        public boolean acceptsURL(String url) {
            return false;
        }

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) {
            throw new UnsupportedOperationException("FakeDriver is never connected to");
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) {
            return new java.sql.DriverPropertyInfo[0];
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
