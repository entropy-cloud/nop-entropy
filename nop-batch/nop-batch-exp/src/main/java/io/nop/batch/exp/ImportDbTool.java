/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.BatchSkipPolicy;
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
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.DataSourceConfig;
import io.nop.dao.jdbc.datasource.HikariDataSourceFactory;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.batch.exp.config.ImportDbConfig;
import io.nop.batch.exp.config.ImportTableConfig;
import io.nop.batch.exp.config.JdbcConnectionConfig;
import io.nop.batch.exp.config.TableFieldConfig;
import io.nop.batch.exp.state.EtlTaskStateStore;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.xlang.api.XLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.nop.batch.exp.EtlToolHelper.getColBinders;

public class ImportDbTool {
    static final Logger LOG = LoggerFactory.getLogger(ImportDbTool.class);

    private ImportDbConfig config;
    private File stateFile;
    private Map<String, Object> args;

    private IResourceLoader inputResourceLoader;

    private EtlTaskStateStore stateStore;

    private IDialect dialect;
    private DataSource dataSource;
    private boolean needClose;

    public void setStateFile(File stateFile) {
        this.stateFile = stateFile;
    }

    public void setConfig(ImportDbConfig config) {
        this.config = config;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
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

    public void addArg(String name, Object value) {
        if (this.args == null)
            args = new LinkedHashMap<>();
        args.put(name, value);
    }

    public void syncConfigWithDb() {
        JdbcConnectionConfig conn = config.getJdbcConnection();
        if (this.dataSource == null) {
            this.needClose = true;
            this.dataSource = buildDataSource(conn);
        }
        if (conn.getDialect() == null) {
            this.dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        } else {
            this.dialect = DialectManager.instance().getDialect(conn.getDialect());
        }
        readTableMetas();

        LOG.info("nop.import-db.config=\n{}", JsonTool.serialize(config, true));
    }

    protected DataSource buildDataSource(JdbcConnectionConfig conn) {
        if (config.getConcurrencyPerTable() != null || config.getThreadCount() > 0) {
            DataSourceConfig config = conn.toDataSourceConfig();
            config.setIdleTimeout(Duration.of(10, ChronoUnit.SECONDS));
            return new HikariDataSourceFactory().newDataSource(config);
        } else {
            return conn.buildDataSource();
        }
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
                    .includeRelations(false).includeUniqueKeys(false).includeIndexes(false)
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
            tableConfig.setKeyFields(table.getPkColumnCodes());
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

            if (old.getKeyFields() == null)
                old.setKeyFields(table.getPkColumnCodes());

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

        if (stateFile != null) {
            stateStore = new EtlTaskStateStore(stateFile);
            if (stateStore.isCompleted()) {
                LOG.info("nop.import-db.skip-since-already-completed");
                return;
            }
        }

        syncConfigWithDb();

        this.inputResourceLoader = new FileResource(new File(config.getInputDir()));

        IThreadPoolExecutor executor;
        if (config.getThreadCount() > 1) {
            executor = DefaultThreadPoolExecutor.newExecutor("import-db", config.getThreadCount(), Integer.MAX_VALUE);
        } else {
            executor = SyncThreadPoolExecutor.INSTANCE;
        }

        IEvalScope scope = XLang.newEvalScope();

        if (config.getBeforeImport() != null)
            config.getBeforeImport().invoke(scope);

        try {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ImportTableConfig tableConfig : config.getTables()) {
                if (!isImportable(tableConfig))
                    continue;

                if (stateStore != null && stateStore.isTableCompleted(tableConfig.getName())) {
                    LOG.info("nop.import-db.skip-table-when-already-completed:tableName={}", tableConfig.getName());
                    continue;
                }
                futures.add(executor.submit(() -> runTask(tableConfig, dataSource, scope), null));
            }
            FutureHelper.getFromFuture(FutureHelper.waitAll(futures));

            if (config.getAfterImport() != null)
                config.getAfterImport().invoke(scope);

            if (stateStore != null)
                stateStore.complete();
        } finally {
            executor.destroy();
            if (needClose) {
                IoHelper.safeClose(dataSource);
                dataSource = null;
            }
        }
    }

    private boolean isImportable(ImportTableConfig tableConfig) {
        if (config.getCheckImportable() == null)
            return true;
        return ConvertHelper.toTruthy(config.getCheckImportable()
                .call1(null, tableConfig, XLang.newEvalScope()));
    }

    protected int getConcurrency(ImportTableConfig tableConfig) {
        if (tableConfig.getConcurrency() != null)
            return tableConfig.getConcurrency();
        if (config.getConcurrencyPerTable() != null)
            return config.getConcurrencyPerTable();
        return 1;
    }

    private void runTask(ImportTableConfig tableConfig, DataSource ds, IEvalScope scope) {
        BatchTaskBuilder<Map<String, Object>, Map<String, Object>> builder = new BatchTaskBuilder<>();
        IBatchLoaderProvider<Map<String, Object>> loader = newLoader(tableConfig);
        if (loader == null) {
            return;
        }

        int concurrency = getConcurrency(tableConfig);
        builder.concurrency(concurrency);

        if (concurrency > 1)
            builder.executor(GlobalExecutors.cachedThreadPool());

        IJdbcTemplate jdbc = JdbcFactory.newJdbcTemplateFor(ds);

        builder.loader(loader);
        if (config.getBatchSize() > 0)
            builder.batchSize(config.getBatchSize());

        if (tableConfig.getMaxSkipCount() != null)
            builder.skipPolicy(new BatchSkipPolicy().maxSkipCount(tableConfig.getMaxSkipCount()));

        builder.processor(newProcessor(tableConfig));
        Map<String, IDataParameterBinder> binders = getColBinders(tableConfig.getFields(), false, dialect);
        IBatchConsumerProvider<Map<String, Object>> consumer = newConsumer(tableConfig, jdbc, binders);

        if (config.isCheckKeyFields() && tableConfig.getKeyFields() != null) {
            Map<String, IDataParameterBinder> colBinders = getColBinders(tableConfig.getKeyFieldConfigs(), false, dialect);

            IBatchConsumerProvider<Map<String, Object>> historyConsumer = null;
            if (Boolean.TRUE.equals(tableConfig.getAllowUpdate())) {
                historyConsumer = new JdbcUpdateBatchConsumer<>(jdbc, dialect, tableConfig.getName(), tableConfig.getKeyFields(), binders);
            }

            IBatchRecordHistoryStore<Map<String, Object>> historyStore = new JdbcKeyDuplicateFilter<>(jdbc, tableConfig.getName(), colBinders);
            builder.historyConsumer(historyConsumer);
            builder.historyStore(historyStore);
        }

        builder.consumer(consumer);

        if (stateStore != null)
            builder.stateStore(stateStore.getTableStore(tableConfig.getName()));

        IBatchTaskContext context = new BatchTaskContextImpl(null, scope);
        context.setTaskName(tableConfig.getName());
        if (args != null)
            context.getEvalScope().setLocalValues(args);

        if (tableConfig.getBeforeImport() != null)
            tableConfig.getBeforeImport().call1(null, tableConfig, context.getEvalScope());

        IBatchTask task = builder.buildTask();

        LOG.info("nop.import-db.begin-import-table:tableName={}", tableConfig.getName());
        context.onAfterComplete(err -> {
            if (err != null) {
                LOG.info("nop.import-db.import-table-fail:tableName={}", tableConfig.getName(), err);
            } else {
                LOG.info("nop.import-db.end-import-table:tableName={}", tableConfig.getName());
                if (tableConfig.getAfterImport() != null)
                    tableConfig.getAfterImport().call1(null, tableConfig, context.getEvalScope());
            }
        });

        task.execute(context);
    }

    private IBatchProcessorProvider<Map<String, Object>, Map<String, Object>> newProcessor(ImportTableConfig tableConfig) {
        return new FieldsProcessor(tableConfig.getFields(), tableConfig.getTransformExpr());
    }


    private IBatchLoaderProvider<Map<String, Object>> newLoader(ImportTableConfig tableConfig) {
        String from = tableConfig.getSourceTableName();
        tableConfig.setFrom(from);
        String format = tableConfig.getFormat();
        if (format == null) {
            format = "csv";
        }

        String resourcePath = from + '.' + format;

        IResource resource = getResource(resourcePath);
        if (!resource.exists()) {
            LOG.info("nop.import.ignore-table-since-no-data-file:file={}", resourcePath);
            return null;
        }

        CsvResourceRecordIO<Map<String, Object>> recordIO = new CsvResourceRecordIO<>();
        recordIO.setRecordType(Map.class);
        return newResourceLoader(recordIO, tableConfig, resource.getName());
    }

    protected IResource getResource(String resourcePath) {
        IResource resource = inputResourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            if (resourcePath.endsWith(".csv")) {
                IResource gzResource = inputResourceLoader.getResource(resourcePath + ".gz");
                if (gzResource.exists())
                    return gzResource;
            }
        }
        return resource;
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

        if (stateStore != null)
            loader.setSaveState(true);

        if (tableConfig.getFilter() != null) {
            IBatchRecordFilter<S, IBatchTaskContext> filter = (record, ctx) ->
                    ConvertHelper.toTruthy(tableConfig.getFilter().call1(null, record, ctx.getEvalScope()));
            loader.setFilter(filter);
        }
        return loader;
    }
}
