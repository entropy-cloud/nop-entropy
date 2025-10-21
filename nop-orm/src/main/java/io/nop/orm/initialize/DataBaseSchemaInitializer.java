/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.initialize;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.OrmConstants;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DataBaseSchemaInitializer {
    static final Logger LOG = LoggerFactory.getLogger(DataBaseSchemaInitializer.class);

    private IJdbcTemplate jdbcTemplate;
    private IOrmSessionFactory ormSessionFactory;

    public static String[] specifyQuerySpaces;

    @InjectValue("@cfg:nop.orm.db-differ.auto-upgrade-database-specify-query-spaces|")
    public void setSpecifyQuerySpaces(String[] specifyQuerySpaces) {
        this.specifyQuerySpaces = specifyQuerySpaces;
    }

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
        Collection<? extends IEntityModel> tables = ormModel.getEntityModelsInTopoOrder();
        for (IEntityModel table : tables) {
            if (table.isTableView())
                continue;

            String querySpace = table.getQuerySpace();
            String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(querySpace)).createTable(table, false);

            // 每个querySpace都对应一个不同的数据库
            if (!jdbcTemplate.existsTable(querySpace, table.getTableName())) {
                jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace).name("create:" + table.getTableName()).sql(createSql).end());
            }
        }
    }

    public static Map<String, List<IEntityModel>> splitByQuerySpace(Collection<IEntityModel> tables) {
        Map<String, List<IEntityModel>> map = new TreeMap<>();
        for (IEntityModel entityModel : tables) {

            String querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
            if(specifyQuerySpaces.length > 0 && !Arrays.stream(specifyQuerySpaces).anyMatch(querySpace::equals)){
                continue;
            }

            Object extProp = entityModel.prop_get(OrmConstants.EXT_AUTO_UPGRADE_DATABASE);
            if (!ConvertHelper.toPrimitiveBoolean(extProp, true, NopException::new))
                continue;
            map.computeIfAbsent(querySpace, k -> new ArrayList<>()).add(entityModel);
        }
        return map;
    }

    /*
     * boolean isEmbedded(){ return jdbcTemplate.runWithConnection(null, conn->{ DatabaseMetaData meta =
     * conn.getMetaData(); return meta.getURL().indexOf(":h2:mem") > 0; }); }
     */
}
