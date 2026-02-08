/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.nop.commons.diff.DiffType;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dbtool.core.diff.OrmDbDiffer;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.initialize.DataBaseSchemaInitializer;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据库升级
 * <p/>
 * 由于无法识别表移除、表更名和字段移除、字段重命名，为确保数据不会被误删除，
 * 其不会执行 `drop table` 和 `drop column` 语句，需自行处理。
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-04-29
 */
public class DataBaseUpgrader {
    private static final Logger LOG = LoggerFactory.getLogger(DataBaseUpgrader.class);

    protected final IJdbcTemplate jdbcTemplate;
    protected final IOrmSessionFactory ormSessionFactory;

    public DataBaseUpgrader(IOrmSessionFactory ormSessionFactory) {
        this(ormSessionFactory.getJdbcTemplate(), ormSessionFactory);
    }

    public DataBaseUpgrader(IJdbcTemplate jdbcTemplate, IOrmSessionFactory ormSessionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.ormSessionFactory = ormSessionFactory;
    }

    public void upgrade() {
        IOrmModel ormModel = this.ormSessionFactory.getOrmModel();
        Map<String, List<IEntityModel>> querySpaceEntities
                = DataBaseSchemaInitializer.splitByQuerySpace(ormModel.getEntityModelsInTopoOrder());

        querySpaceEntities.forEach(this::upgradeByQuerySpace);
    }

    /**
     * 按 querySpace 得到并执行各个数据源的数据库升级脚本，但仅执行对表做更新或新增的 DDL，
     * 不执行表/字段删除，以避免误删除数据
     */
    private void upgradeByQuerySpace(String querySpace, List<IEntityModel> newEntities) {
        String sqlName = "db-differ-upgrade:" + querySpace;

        this.jdbcTemplate.runWithConnection(SQL.begin().querySpace(querySpace).name(sqlName).end(), (conn) -> {
            String sql = genUpgradeSql(conn, newEntities);

            if (sql == null) {
                LOG.warn("nop.orm.db-differ.upgrade.no-change-exists:querySpace={}", querySpace);
                return null;
            }
            LOG.info("nop.orm.db-differ.upgrade:querySpace={},sql={}", querySpace, sql);

            this.jdbcTemplate.executeMultiSql(SQL.begin().querySpace(querySpace).name(sqlName).sql(sql).end());
            return null;
        });
    }

    private String genUpgradeSql(Connection conn, List<IEntityModel> newEntities) {
        JdbcMetaDiscovery discovery = JdbcMetaDiscovery.forConnection(conn);
        // Note：由于自动处理的情况无法确定表的匹配模式，故而，只能将库内的表都查出来再做过滤
        DataBaseMeta oldDbMeta = discovery.discover(null, null, "%");

        Set<String> newEntityTableNames = newEntities.stream()
                                                     // 表名大小写与逆向出的 ORM 模型需保持一致
                                                     .map(entity -> discovery.normalizeTableName(entity.getTableName()))
                                                     .collect(Collectors.toSet());
        List<IEntityModel> oldEntities = oldDbMeta.getOrmModel()
                                                  .getEntities()
                                                  .stream()
                                                  // 仅做表更新或新增，不做表删除
                                                  .filter(entity -> newEntityTableNames.contains(entity.getTableName()))
                                                  .collect(Collectors.toList());

        List<OrmDbDiffer.DiffDdl> diffDdlList = OrmDbDiffer.forDialect(discovery.getDialect())
                                                           .genDiffDdl(oldEntities, newEntities)
                                                           .stream()
                                                           // 过滤掉对字段的删除，因为其可能是重命名，而当前无法识别重命名变更
                                                           .filter((diffDdl) -> diffDdl.getType() != DiffType.remove
                                                                                || diffDdl.getTargetType()
                                                                                   != OrmDbDiffer.DiffDdlTargetType.column)
                                                           .collect(Collectors.toList());

        return OrmDbDiffer.DiffDdl.toSql(diffDdlList);
    }
}
