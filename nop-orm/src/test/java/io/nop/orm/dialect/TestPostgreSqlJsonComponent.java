package io.nop.orm.dialect;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.orm.dao.TestJsonComponent;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZoneId;

@EnabledIfSystemProperty(named = "nop.test.docker.enabled", matches = "true")
@Testcontainers
public class TestPostgreSqlJsonComponent extends TestJsonComponent {
    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.testcontainers.jdbc.ContainerDatabaseDriver");
        // String timeZone = ZoneId.systemDefault().getId();
        ds.setJdbcUrl("jdbc:tc:postgresql:latest:///test?TC_DAEMON=true");
        ds.setUsername(AppConfig.var("nop.database.username", "test"));
        ds.setPassword(AppConfig.var("nop.database.password", "test"));
        ds.setMaximumPoolSize(2);
        return ds;
    }

}
