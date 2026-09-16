package io.nop.metadata.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmTemplate;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.api.dto.ErrorDTO;
import io.nop.metadata.api.dto.ProfileResultDTO;
import io.nop.metadata.api.dto.ProfilingColumnStatsDTO;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.profiling.ProfilingColumnStats;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import io.nop.metadata.service.query.FilterToSqlTranslator;
import io.nop.metadata.service.query.MetaEntityQueryExecutor;
import io.nop.metadata.service.sqlview.SqlSelectFieldExtractor;
import io.nop.metadata.service.sqlview.SqlViewField;
import io.nop.metadata.service.sqlview.SqlViewFieldTypeInferrer;
import io.nop.metadata.service.tableref.MetaEntityReferenceResolver;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.metadata.service.NopMetadataException;

import static io.nop.metadata.service.query.AggregationHelper.safeProductName;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NopMetaEntityQueryAction {

    private final SqlSelectFieldExtractor sqlFieldExtractor = new SqlSelectFieldExtractor();
    private final MetaEntityFieldResolver fieldResolver = new MetaEntityFieldResolver(sqlFieldExtractor);
    private final MetaDataSourceResolver dataSourceResolver = new MetaDataSourceResolver();
    private final MetaEntityReferenceResolver tableRefResolver = new MetaEntityReferenceResolver(
            dataSourceResolver, fieldResolver);
    private final FilterToSqlTranslator filterTranslator = new FilterToSqlTranslator();
    private SqlViewFieldTypeInferrer sqlFieldTypeInferrer;
    private TableReferenceExecutor tableRefExecutor;

    private static final Set<String> SUPPORTED_DIALECTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    /**
     * 概念缩减（plan 2261）：物理实体路径直接使用实体行自身的 entityName（不再经
     * baseEntityId 间接寻址——原逻辑表→实体配对已随表概念删除而消失）。
     */
    public List<Map<String, Object>> queryPhysicalData(NopMetaEntity entity, TreeBean filter, Long limit, Long offset,
                                                       IDaoProvider daoProvider, IOrmTemplate orm) {
        String entityName = entity.getEntityName();
        if (entityName == null || entityName.isEmpty() || !orm.isValidEntityName(entityName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_ENTITY_NOT_REGISTERED)
                    .param("metaEntityId", entity.getMetaEntityId())
                    .param("entityName", String.valueOf(entityName));
        }
        io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity> targetDao = castToOrmEntityDao(daoProvider.dao(entityName));
        QueryBean query = new QueryBean();
        if (filter != null) query.setFilter(filter);
        if (limit != null) query.setLimit(limit.intValue());
        if (offset != null) query.setOffset(offset.intValue());
        List<io.nop.orm.IOrmEntity> entities = targetDao.findAllByQuery(query);
        io.nop.orm.model.IEntityModel entityModel = targetDao.getEntityModel();
        List<String> propNames = new ArrayList<>();
        for (io.nop.orm.model.IColumnModel col : entityModel.getColumns()) {
            propNames.add(col.getName());
        }
        List<Map<String, Object>> items = new ArrayList<>(entities.size());
        for (io.nop.orm.IOrmEntity row : entities) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String prop : propNames) {
                map.put(prop, row.orm_propValueByName(prop));
            }
            items.add(map);
        }
        return items;
    }

    public List<Map<String, Object>> queryExternalData(NopMetaEntity table, TreeBean filter, Long limit, Long offset,
                                                        IMetaDataSourceConnectionProcessor connectionService,
                                                        IDaoProvider daoProvider, IOrmTemplate orm) {
        NopMetaDataSource dataSource = resolveQueryDataSourceOrThrow(table, daoProvider);
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        List<ResolvedTableField> fields = fieldResolver.resolve(table, fieldDao);
        List<String> columns = new ArrayList<>(fields.size());
        for (ResolvedTableField f : fields) {
            columns.add(f.getName());
        }
        final List<Map<String, Object>>[] holder = newArrayHolder();
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    requireSupportedDialect(productName, table.getMetaEntityId());
                    FilterToSqlTranslator.TranslatedFilter tf = filterTranslator.translate(filter);
                    String sql = MetaEntityQueryExecutor.buildExternalSelectSql(table.getTableName(), columns, tf.getSql(), limit, offset, productName);
                    holder[0] = MetaEntityQueryExecutor.executeQuery(conn, sql,
                            tf.getParams(), limit, offset,
                            table.getMetaEntityId());
                });
        return holder[0];
    }

    public List<Map<String, Object>> querySqlData(NopMetaEntity table, TreeBean filter, Long limit, Long offset,
                                                   IMetaDataSourceConnectionProcessor connectionService,
                                                   IDaoProvider daoProvider, IOrmTemplate orm) {
        String sourceSql = table.getSourceSql();
        if (sourceSql == null || sourceSql.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_SQL_SOURCE_EMPTY).param("metaEntityId", table.getMetaEntityId());
        }
        NopMetaDataSource dataSource = resolveQueryDataSourceOrThrow(table, daoProvider);
        final List<Map<String, Object>>[] holder = newArrayHolder();
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    requireSupportedDialect(productName, table.getMetaEntityId());
                    FilterToSqlTranslator.TranslatedFilter tf = filterTranslator.translate(filter);
                    String sql = MetaEntityQueryExecutor.buildSqlSelectSql(sourceSql, tf.getSql(), limit, offset, productName);
                    holder[0] = MetaEntityQueryExecutor.executeQuery(conn, sql,
                            tf.getParams(), limit, offset,
                            table.getMetaEntityId());
                });
        return holder[0];
    }

    private NopMetaDataSource resolveQueryDataSourceOrThrow(NopMetaEntity table, IDaoProvider daoProvider) {
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        try {
            return dataSourceResolver.resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            if (e.getParam("metaEntityId") == null) {
                e.param("metaEntityId", table.getMetaEntityId());
            }
            throw e;
        }
    }

    private static void requireSupportedDialect(String databaseProductName, String metaEntityId) {
        if (databaseProductName == null || !SUPPORTED_DIALECTS.contains(databaseProductName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUERY_UNSUPPORTED_DIALECT)
                    .param("databaseProductName", String.valueOf(databaseProductName))
                    .param("metaEntityId", metaEntityId);
        }
    }

    private TableReferenceExecutor ensureTableRefExecutor(IMetaDataSourceConnectionProcessor connectionService,
                                                           IOrmTemplate orm) {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm);
        }
        return tableRefExecutor;
    }

    public SqlViewFieldTypeInferrer ensureSqlFieldTypeInferrer(
            IMetaDataSourceConnectionProcessor connectionService) {
        if (sqlFieldTypeInferrer == null) {
            sqlFieldTypeInferrer = new SqlViewFieldTypeInferrer(dataSourceResolver, connectionService);
        }
        return sqlFieldTypeInferrer;
    }

    public MetaEntityReferenceResolver tableRefResolver() {
        return tableRefResolver;
    }

    public SqlSelectFieldExtractor sqlFieldExtractor() {
        return sqlFieldExtractor;
    }

    public MetaEntityFieldResolver fieldResolver() {
        return fieldResolver;
    }

    public MetaDataSourceResolver dataSourceResolver() {
        return dataSourceResolver;
    }

    public FilterToSqlTranslator filterTranslator() {
        return filterTranslator;
    }

    static ProfileResultDTO buildProfileResultDTO(NopMetaProfilingResult row, ProfilingSnapshot snapshot) {
        ProfileResultDTO dto = new ProfileResultDTO();
        dto.setProfilingResultId(row.getProfilingResultId());
        dto.setColumnCount(snapshot.getColumnStats().size());
        dto.setUnavailable(new ArrayList<>(snapshot.getTableUnavailable()));
        List<ProfilingColumnStatsDTO> columnDTOs = new ArrayList<>(snapshot.getColumnStats().size());
        for (ProfilingColumnStats cs : snapshot.getColumnStats()) {
            ProfilingColumnStatsDTO colDTO = new ProfilingColumnStatsDTO();
            colDTO.setColumnName(cs.getColumnName());
            colDTO.setRowCount(cs.getTotalCount());
            colDTO.setNullCount(cs.getNullCount());
            colDTO.setMinValue(cs.getMinValue());
            colDTO.setMaxValue(cs.getMaxValue());
            columnDTOs.add(colDTO);
        }
        dto.setColumns(columnDTOs);
        List<ErrorDTO> errorDTOs = new ArrayList<>(snapshot.getErrors().size());
        for (Map<String, Object> err : snapshot.getErrors()) {
            ErrorDTO errDTO = new ErrorDTO();
            Object colName = err.get("columnName");
            errDTO.setCode(colName != null ? colName.toString() : null);
            Object msg = err.get("error");
            errDTO.setMessage(msg != null ? msg.toString() : null);
            errorDTOs.add(errDTO);
        }
        dto.setErrors(errorDTOs);
        return dto;
    }

    private static List<Map<String, Object>>[] newArrayHolder() {
        return MetaEntityQueryExecutor.newArrayHolder();
    }

    @SuppressWarnings("unchecked")
    private static io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity> castToOrmEntityDao(io.nop.dao.api.IEntityDao<?> dao) {
        return (io.nop.orm.dao.IOrmEntityDao<io.nop.orm.IOrmEntity>) dao;
    }
}
