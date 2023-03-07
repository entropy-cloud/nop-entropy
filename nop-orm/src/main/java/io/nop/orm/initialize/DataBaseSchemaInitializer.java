/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.initialize;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collection;

public class DataBaseSchemaInitializer {
    static final Logger LOG = LoggerFactory.getLogger(DataBaseSchemaInitializer.class);

    private IJdbcTemplate jdbcTemplate;
    private IOrmSessionFactory ormSessionFactory;

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setOrmSessionFactory(IOrmSessionFactory ormSessionFactory) {
        this.ormSessionFactory = ormSessionFactory;
    }

    @PostConstruct
    public void init() {
        IOrmModel ormModel = ormSessionFactory.getOrmModel();
        Collection<IEntityModel> tables = ormModel.getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(tables, false);

        try {
            jdbcTemplate.executeMultiSql(new SQL(createSql));
        } catch (Exception e) {
            LOG.trace("nop.orm.create-schema-fail", e);
        }
    }

    /*
     * boolean isEmbedded(){ return jdbcTemplate.runWithConnection(null, conn->{ DatabaseMetaData meta =
     * conn.getMetaData(); return meta.getURL().indexOf(":h2:mem") > 0; }); }
     */
}
