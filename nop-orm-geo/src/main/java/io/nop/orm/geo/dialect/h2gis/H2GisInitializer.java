package io.nop.orm.geo.dialect.h2gis;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import org.h2gis.functions.factory.H2GISFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Map;

public class H2GisInitializer {
    static final Logger LOG = LoggerFactory.getLogger(H2GisInitializer.class);

    @Inject
    IJdbcTemplate jdbcTemplate;

    private Map<String, String> querySpaceToDialects;

    public void setQuerySpaceToDialectConfig(String config) {
        this.querySpaceToDialects = StringHelper.parseStringMap(config, '=', ',');
    }

    public void init() {
        if (querySpaceToDialects != null) {
            for (Map.Entry<String, String> entry : querySpaceToDialects.entrySet()) {
                String dialect = entry.getValue();
                String querySpace = entry.getKey();
                if ("h2gis".equals(dialect)) {
                    LOG.info("nop.orm.init-h2gis");
                    SQL sql = SQL.begin().sql(querySpace).sql("init").end();
                    jdbcTemplate.runWithConnection(sql, conn -> {
                        try {
                            H2GISFunctions.load(conn);
                        } catch (SQLException e) {
                            throw NopException.adapt(e);
                        }
                        return null;
                    });
                }
            }
        }
    }
}
