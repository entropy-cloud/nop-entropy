/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.loader.ResourceRecordLoader;
import io.nop.batch.jdbc.consumer.JdbcInsertBatchConsumer;
import io.nop.batch.jdbc.consumer.JdbcInsertDuplicateFilter;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.query.BeanVariableScope;
import io.nop.core.model.query.QueryBeanHelper;
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
import io.nop.dbtool.exp.config.ImportDbConfig;
import io.nop.dbtool.exp.config.ImportTableConfig;
import io.nop.dbtool.exp.config.JdbcConnectionConfig;
import io.nop.orm.eql.utils.OrmDialectHelper;
import io.nop.orm.model.OrmEntityModel;

import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.nop.dbtool.exp.DbToolExpErrors.ARG_TABLE_NAME;
import static io.nop.dbtool.exp.DbToolExpErrors.ERR_EXP_UNDEFINED_TABLE;

public class ImportDbTool {
    private ImportDbConfig config;

    private IResourceLoader inputResourceLoader;

    private IDialect dialect;

    public void setConfig(ImportDbConfig config) {
        this.config = config;
    }

    public ImportDbConfig getConfig() {
        return config;
    }

    public void setConfigPath(String configPath) {
        this.setConfig((ImportDbConfig) ResourceComponentManager.instance().loadComponentModel(configPath));
    }

    public void execute() {
        Guard.notEmpty(config.getJdbcConnection(), "jdbc-connection");

        this.inputResourceLoader = new FileResource(new File(config.getInputDir()));

        IThreadPoolExecutor executor;
        if (config.getThreadCount() > 1) {
            executor = DefaultThreadPoolExecutor.newExecutor("import-db", config.getThreadCount(), Integer.MAX_VALUE);
        } else {
            executor = SyncThreadPoolExecutor.INSTANCE;
        }

        JdbcConnectionConfig conn = config.getJdbcConnection();
        try {
            DataSource ds = conn.buildDataSource();
            this.dialect = DialectManager.instance().getDialectForDataSource(ds);

            DataBaseMeta meta = JdbcMetaDiscovery.forDataSource(ds).discover(conn.getCatalog(), null, null);

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ImportTableConfig tableConfig : getAllTables().values()) {
                futures.add(executor.submit(() -> runTask(tableConfig, ds, meta), null));
            }
            FutureHelper.getFromFuture(FutureHelper.waitAll(futures));
        } finally {
            executor.destroy();
        }
    }

    private Map<String, ImportTableConfig> getAllTables() {
        Map<String, ImportTableConfig> map = new LinkedHashMap<>();
        if (config.getTables() != null) {
            config.getTables().forEach(table -> map.put(StringHelper.lowerCase(table.getName()), table));
        }

        Collection<? extends IResource> resources = inputResourceLoader.getChildren(".");
        if (config.isImportAllTables()) {
            for (IResource resource : resources) {
                if (resource.isDirectory())
                    continue;

                String name = resource.getName();
                if (name.endsWith(".csv") || name.endsWith(".csv.gz")) {
                    String tableName = StringHelper.lowerCase(StringHelper.firstPart(name, '.'));


                    if (config.getExcludeTableNames() != null && config.getExcludeTableNames().contains(name))
                        continue;

                    if (!map.containsKey(tableName)) {
                        ImportTableConfig tableConfig = new ImportTableConfig();
                        tableConfig.setName(tableName);
                        map.put(tableName, tableConfig);
                    }
                }
            }
        }
        return map;
    }

    private void runTask(ImportTableConfig tableConfig, DataSource ds, DataBaseMeta meta) {
        BatchTaskBuilder builder = new BatchTaskBuilder();
        IBatchLoader loader = newLoader(tableConfig);
        if (loader == null) {
            return;
        }

        OrmEntityModel tableModel = meta.getTable(tableConfig.getName());
        if (tableModel == null)
            throw new NopException(ERR_EXP_UNDEFINED_TABLE).param(ARG_TABLE_NAME, tableConfig.getName());


        IJdbcTemplate jdbc = JdbcFactory.newJdbcTemplateFor(ds);

        builder.loader(loader);
        builder.batchSize(config.getBatchSize());
        builder.consumer(newConsumer(tableModel, jdbc));

        if (config.isIgnoreDuplicate()) {
            Map<String, IDataParameterBinder> colBinders = OrmDialectHelper.getPkColBinders(tableModel, dialect, true);
            builder.historyStore(new JdbcInsertDuplicateFilter(jdbc, tableConfig.getName(), colBinders));
        }

        IBatchTask task = builder.build();
        IBatchTaskContext context = new BatchTaskContextImpl();
        task.execute(context);
    }

    private IBatchLoader<Map<String, Object>, IBatchChunkContext> newLoader(ImportTableConfig tableConfig) {
        String from = tableConfig.getSourceName();
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

    private IBatchConsumer<Map<String, Object>, IBatchChunkContext> newConsumer(OrmEntityModel tableModel,
                                                                                IJdbcTemplate jdbc) {

        Map<String, IDataParameterBinder> binders = OrmDialectHelper.getEntityColBinders(tableModel, dialect, true);
        JdbcInsertBatchConsumer<Map<String, Object>, IBatchChunkContext> consumer =
                new JdbcInsertBatchConsumer<>(jdbc, this.dialect, tableModel.getTableName(), binders);
        return consumer;
    }

    private <S, C> ResourceRecordLoader<S, C> newResourceLoader(IResourceRecordIO<S> recordIO,
                                                                ImportTableConfig tableConfig,
                                                                String resourcePath) {
        ResourceRecordLoader<S, C> loader = new ResourceRecordLoader<>();
        loader.setRecordIO(recordIO);
        loader.setResourcePath(resourcePath);
        loader.setResourceLoader(inputResourceLoader);

        if (tableConfig.getFilter() != null) {
            IBatchRecordFilter<S> filter = (record, ctx) ->
                    QueryBeanHelper.evaluateFilter(tableConfig.getFilter(), new BeanVariableScope(record));
            loader.setFilter(filter);
        }
        return loader;
    }
}
