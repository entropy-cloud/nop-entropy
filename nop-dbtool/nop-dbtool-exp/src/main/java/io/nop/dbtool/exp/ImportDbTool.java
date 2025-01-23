/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.exp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.batch.jdbc.consumer.JdbcInsertBatchConsumer;
import io.nop.batch.jdbc.consumer.JdbcKeyDuplicateFilter;
import io.nop.batch.jdbc.consumer.JdbcUpdateBatchConsumer;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.dbtool.exp.config.IFieldConfig;
import io.nop.dbtool.exp.config.ImportDbConfig;
import io.nop.dbtool.exp.config.ImportTableConfig;
import io.nop.dbtool.exp.config.JdbcConnectionConfig;
import io.nop.dbtool.exp.config.TableFieldConfig;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.OrmEntityModel;

import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ImportDbTool {
    private ImportDbConfig config;
    private Map<String, Object> args;

    private IResourceLoader inputResourceLoader;

    private IDialect dialect;
    private DataSource dataSource;

    public void setConfig(ImportDbConfig config) {
        this.config = config;
    }

    public ImportDbConfig getConfig() {
        return config;
    }

    public void setConfigPath(String configPath) {
        this.setConfig((ImportDbConfig) ResourceComponentManager.instance().loadComponentModel(configPath));
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public void syncConfigWithDb() {
        JdbcConnectionConfig conn = config.getJdbcConnection();
        this.dataSource = conn.buildDataSource();
        if (conn.getDialect() == null) {
            this.dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        } else {
            this.dialect = DialectManager.instance().getDialect(conn.getDialect());
        }
        readTableMetas();
    }

    private void readTableMetas() {
        if (config.getExcludeTableNames() != null) {
            config.getTables().removeIf(table -> {
                return config.getExcludeTableNames().contains(table.getName());
            });
        }

        if (config.isNeedDatabaseMeta()) {
            JdbcConnectionConfig conn = config.getJdbcConnection();

            String tableNamePattern = config.getTableNamePattern();

            DataBaseMeta meta = JdbcMetaDiscovery.forDataSource(dataSource)
                    .discover(conn.getCatalog(), config.getSchemaPattern(), tableNamePattern);

            for (OrmEntityModel table : meta.getTables().values()) {
                String name = StringHelper.lowerCase(table.getTableName());

                if (config.getExcludeTableNames() != null && config.getExcludeTableNames().contains(name))
                    continue;

                if (!config.isImportAllTables()) {
                    if (!config.hasTable(name))
                        continue;
                }

                mergeTableConfig(name, table);
            }
        }
    }

    private void mergeTableConfig(String name, OrmEntityModel table) {
        ImportTableConfig old = config.getTable(name);
        if (old == null) {
            ImportTableConfig tableConfig = new ImportTableConfig();
            tableConfig.setName(name);
            tableConfig.setKeyFields(table.getPkColumnNames());
            for (IColumnModel col : table.getColumns()) {
                TableFieldConfig field = new TableFieldConfig();
                field.setName(col.getCode());
                field.setStdDataType(col.getStdDataType());
                field.setStdSqlType(col.getStdSqlType());
                tableConfig.addField(field);
            }
            config.addTable(tableConfig);
        } else {
            if (!old.isImportAllFields())
                return;

            for (IColumnModel col : table.getColumns()) {
                if (old.hasField(col.getCode()))
                    continue;

                TableFieldConfig field = new TableFieldConfig();
                field.setName(col.getCode());
                field.setStdDataType(col.getStdDataType());
                field.setStdSqlType(col.getStdSqlType());
                old.addField(field);
            }
        }
    }

    public void execute() {
        Guard.notEmpty(config.getJdbcConnection(), "jdbc-connection");

        syncConfigWithDb();

        this.inputResourceLoader = new FileResource(new File(config.getInputDir()));

        IThreadPoolExecutor executor;
        if (config.getThreadCount() > 1) {
            executor = DefaultThreadPoolExecutor.newExecutor("import-db", config.getThreadCount(), Integer.MAX_VALUE);
        } else {
            executor = SyncThreadPoolExecutor.INSTANCE;
        }

        try {

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ImportTableConfig tableConfig : config.getTables()) {
                futures.add(executor.submit(() -> runTask(tableConfig, dataSource), null));
            }
            FutureHelper.getFromFuture(FutureHelper.waitAll(futures));
        } finally {
            executor.destroy();
        }
    }

    private void runTask(ImportTableConfig tableConfig, DataSource ds) {
        BatchTaskBuilder<Map<String, Object>, Map<String, Object>> builder = new BatchTaskBuilder<>();
        IBatchLoaderProvider<Map<String, Object>> loader = newLoader(tableConfig);
        if (loader == null) {
            return;
        }

        IJdbcTemplate jdbc = JdbcFactory.newJdbcTemplateFor(ds);

        builder.loader(loader);
        if (config.getBatchSize() > 0)
            builder.batchSize(config.getBatchSize());

        builder.processor(newProcessor(tableConfig));
        Map<String, IDataParameterBinder> binders = getColBinders(tableConfig.getFields());
        IBatchConsumerProvider<Map<String, Object>> consumer = newConsumer(tableConfig, jdbc, binders);


        if (config.isCheckKeyFields() && tableConfig.getKeyFields() != null) {
            Map<String, IDataParameterBinder> colBinders = getColBinders(tableConfig.getKeyFieldConfigs());

            IBatchConsumerProvider<Map<String, Object>> historyConsumer = null;
            if (Boolean.TRUE.equals(tableConfig.getAllowUpdate())) {
                historyConsumer = new JdbcUpdateBatchConsumer<>(jdbc, dialect, tableConfig.getName(), tableConfig.getKeyFields(), binders);
            }

            IBatchRecordHistoryStore<Map<String, Object>> historyStore = new JdbcKeyDuplicateFilter<>(jdbc, tableConfig.getName(), colBinders);
            builder.historyConsumer(historyConsumer);
            builder.historyStore(historyStore);
        }

        builder.consumer(consumer);

        IBatchTaskContext context = new BatchTaskContextImpl();
        if (args != null)
            context.getEvalScope().setLocalValues(args);

        IBatchTask task = builder.buildTask();
        task.execute(context);
    }

    private IBatchProcessorProvider<Map<String, Object>, Map<String, Object>> newProcessor(ImportTableConfig tableConfig) {
        return new FieldsProcessor(tableConfig.getFields(), tableConfig.getTransformExpr());
    }

    private Map<String, IDataParameterBinder> getColBinders(List<? extends IFieldConfig> cols) {
        Map<String, IDataParameterBinder> binders = CollectionHelper.newCaseInsensitiveMap(cols.size());

        for (IFieldConfig col : cols) {
            String key = col.getName();
            IDataParameterBinder binder = dialect.getDataParameterBinder(col.getStdDataType(), col.getStdSqlType());
            binders.put(key, binder);
        }
        return binders;
    }

    private IBatchLoaderProvider<Map<String, Object>> newLoader(ImportTableConfig tableConfig) {
        String from = tableConfig.getSourceTableName();
        tableConfig.setFrom(from);
        String format = tableConfig.getFormat();
        if (format == null) {
            format = "csv";
            tableConfig.setFormat(format);
        }

        String resourcePath = from + '.' + format;

        IResource resource = inputResourceLoader.getResource(resourcePath);
        if (!resource.exists())
            return null;

        CsvResourceRecordIO<Map<String, Object>> recordIO = new CsvResourceRecordIO<>();
        recordIO.setRecordType(Map.class);
        return newResourceLoader(recordIO, tableConfig, resourcePath);
    }

    private IBatchConsumerProvider<Map<String, Object>> newConsumer(ImportTableConfig tableConfig,
                                                                    IJdbcTemplate jdbc, Map<String, IDataParameterBinder> binders) {
        JdbcInsertBatchConsumer<Map<String, Object>> consumer =
                new JdbcInsertBatchConsumer<>(jdbc, this.dialect, tableConfig.getName(), binders);
        return consumer;
    }

    private <S> ResourceRecordLoaderProvider<S> newResourceLoader(IResourceRecordIO<S> recordIO,
                                                                  ImportTableConfig tableConfig,
                                                                  String resourcePath) {
        ResourceRecordLoaderProvider<S> loader = new ResourceRecordLoaderProvider<>();
        loader.setRecordIO(recordIO);
        loader.setResourcePath(resourcePath);
        loader.setResourceLocator(inputResourceLoader);

        if (tableConfig.getFilter() != null) {
            IBatchRecordFilter<S, IBatchTaskContext> filter = (record, ctx) ->
                    ConvertHelper.toTruthy(tableConfig.getFilter().call1(null, record, ctx.getEvalScope()));
            loader.setFilter(filter);
        }
        return loader;
    }
}
