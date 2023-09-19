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
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        Map<String, List<IEntityModel>> querySpaceTables = splitByQuerySpace(tables);

        for (Map.Entry<String, List<IEntityModel>> entry : querySpaceTables.entrySet()) {
            String querySpace = entry.getKey();
            List<IEntityModel> list = entry.getValue();
            String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(list, false);

            // 每个querySpace都对应一个不同的数据库
            try {
                jdbcTemplate.executeMultiSql(SQL.begin().querySpace(querySpace).name("dbInit").sql(createSql).end());
            } catch (Exception e) {
                LOG.trace("nop.orm.create-schema-fail", e);
            }
        }
    }

    Map<String, List<IEntityModel>> splitByQuerySpace(Collection<IEntityModel> tables) {
        Map<String, List<IEntityModel>> map = new TreeMap<>();
        for (IEntityModel entityModel : tables) {
            String querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
            map.computeIfAbsent(querySpace, k -> new ArrayList<>()).add(entityModel);
        }
        return map;
    }

    /*
     * boolean isEmbedded(){ return jdbcTemplate.runWithConnection(null, conn->{ DatabaseMetaData meta =
     * conn.getMetaData(); return meta.getURL().indexOf(":h2:mem") > 0; }); }
     */
}
