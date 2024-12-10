package io.nop.batch.dsl.manager;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.dsl.BatchDslConstants;
import io.nop.batch.dsl.model.BatchJdbcReaderModel;
import io.nop.batch.dsl.model.BatchJdbcWriterModel;
import io.nop.batch.jdbc.consumer.JdbcBatchConsumerProvider;
import io.nop.batch.jdbc.loader.JdbcBatchLoaderProvider;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;

import static io.nop.batch.dsl.manager.OrmBatchSupport.newQueryBuilder;

public class JdbcBatchSupport {

    public static IBatchLoaderProvider<Object> newJdbcReader(BatchJdbcReaderModel loaderModel,
                                                             IBeanProvider beanProvider,
                                                             IJdbcTemplate jdbcTemplate,
                                                             INamedSqlBuilder sqlLibManager) {
        JdbcBatchLoaderProvider<Object> loader = new JdbcBatchLoaderProvider<>();
        loader.setQuerySpace(loaderModel.getQuerySpace());
        loader.setJdbcTemplate(jdbcTemplate);
        if (loaderModel.getFetchSize() != null)
            loader.setFetchSize(loaderModel.getFetchSize());
        if (loaderModel.getMaxFieldSize() != null)
            loader.setMaxFieldSize(loaderModel.getMaxFieldSize());
        if (loaderModel.getQueryTimeout() != null) {
            loader.setQueryTimeout(loaderModel.getQueryTimeout());
        }

        loader.setPartitionIndexField(loaderModel.getPartitionIndexField());

        if (loaderModel.isStreaming())
            loader.setStreaming(true);

        if (loaderModel.getMaxRows() != null) {
            loader.setMaxRows(loaderModel.getMaxRows());
        }

        if (loaderModel.getRowMapper() != null) {
            if (loaderModel.getRowMapper().equals(BatchDslConstants.ROW_MAPPER_CAMEL_CASE)) {
                loader.setRowMapper((IRowMapper) ColumnMapRowMapper.CAMEL_CASE);
            } else {
                String beanName = BatchDslConstants.ROW_MAPPER_BEAN_PREFIX + loaderModel.getRowMapper();
                loader.setRowMapper((IRowMapper<Object>) beanProvider.getBean(beanName));
            }
        }

        if (loaderModel.getQuery() != null) {
            loader.setQueryBuilder(newQueryBuilder(loaderModel.getQuery()));
        }

        if (loaderModel.getSqlName() != null) {
            loader.setSqlName(loaderModel.getSqlName());
            loader.setNamedSqlBuilder(sqlLibManager);
        }

        if (loaderModel.getSql() != null)
            loader.setSqlGenerator(loaderModel.getSql());

        return loader;
    }

    public static IBatchConsumerProvider<Object> newJdbcWriter(BatchJdbcWriterModel consumerModel,
                                                               IJdbcTemplate jdbcTemplate) {
        JdbcBatchConsumerProvider<Object> provider = new JdbcBatchConsumerProvider<>();
        provider.setJdbcTemplate(jdbcTemplate);
        provider.setKeyFields(consumerModel.getKeyFields());
        provider.setAllowInsert(consumerModel.isAllowInsert());
        provider.setAllowUpdate(consumerModel.isAllowUpdate());
        provider.setFields(consumerModel.getFields());
        provider.setTableName(consumerModel.getTableName());
        provider.setQuerySpace(consumerModel.getQuerySpace());
        return provider;
    }
}
