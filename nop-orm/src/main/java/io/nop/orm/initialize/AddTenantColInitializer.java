package io.nop.orm.initialize;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModelConstants;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 从不使用租户升级到使用租户
 */
public class AddTenantColInitializer {
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
        Collection<? extends IEntityModel> tables = ormModel.getEntityModels();
        Map<String, List<IEntityModel>> querySpaceTables = splitByQuerySpace(tables);

        long beginTime = CoreMetrics.currentTimeMillis();
        for (Map.Entry<String, List<IEntityModel>> entry : querySpaceTables.entrySet()) {
            String querySpace = entry.getKey();
            List<IEntityModel> list = entry.getValue();
            DdlSqlCreator sqlCreator = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(querySpace));

            for (IEntityModel entityModel : list) {
                if (!entityModel.isUseTenant())
                    continue;

                IColumnModel col = entityModel.getTenantColumn();
                if (col != null && col.getName().equals(OrmModelConstants.PROP_NAME_nopTenantId)) {
                    String addSql = sqlCreator.addTenantIdForTable(entityModel,true);
                    try {
                        jdbcTemplate.executeMultiSql(SQL.begin().sql(addSql).querySpace(querySpace).name("add_tenant").end());
                        LOG.info("nop.orm.add-tenant-col:table={},col={}", entityModel.getTableName(), col.getCode());
                    } catch (Exception e) {
                        LOG.trace("nop.orm.add-tenant-col-fail", e);
                    }
                }
            }
        }

        long diff = CoreMetrics.currentTimeMillis() - beginTime;
        LOG.info("nop.orm.add-tenant-col-finished:usedTime={}ms", diff);
    }

    Map<String, List<IEntityModel>> splitByQuerySpace(Collection<? extends IEntityModel> tables) {
        Map<String, List<IEntityModel>> map = new TreeMap<>();
        for (IEntityModel entityModel : tables) {
            String querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
            map.computeIfAbsent(querySpace, k -> new ArrayList<>()).add(entityModel);
        }
        return map;
    }

}
