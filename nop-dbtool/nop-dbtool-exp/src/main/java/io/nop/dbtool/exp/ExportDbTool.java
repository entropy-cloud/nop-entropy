package io.nop.dbtool.exp;

import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumer;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.consumer.MultiBatchConsumer;
import io.nop.batch.core.consumer.ResourceRecordConsumer;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.jdbc.consumer.GenInsertSqlRecordIO;
import io.nop.batch.jdbc.loader.JdbcBatchLoader;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.dbtool.core.discovery.jdbc.JdbcMetaDiscovery;
import io.nop.dbtool.exp.config.ExportDbConfig;
import io.nop.dbtool.exp.config.ExportTableConfig;
import io.nop.orm.model.OrmEntityModel;

import javax.sql.DataSource;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ExportDbTool {
    private ExportDbConfig config;

    private IResourceLoader outputResourceLoader;

    private IDialect dialect;

    public void setConfig(ExportDbConfig config) {
        this.config = config;
    }

    public void execute() {
        this.outputResourceLoader = new FileResource(new File(config.getOutputDir()));

        IThreadPoolExecutor executor;
        if (config.getThreadCount() > 1) {
            executor = DefaultThreadPoolExecutor.newExecutor("export-db", config.getThreadCount(), 10);
        } else {
            executor = SyncThreadPoolExecutor.INSTANCE;
        }

        try {
            DataSource ds = config.buildDataSource();
            if (config.getDialect() == null) {
                this.dialect = DialectManager.instance().getDialectForDataSource(ds);
            } else {
                this.dialect = DialectManager.instance().getDialect(config.getDialect());
            }

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (ExportTableConfig tableConfig : getAllTables().values()) {
                futures.add(executor.submit(() -> runTask(tableConfig, ds), null));
            }
            FutureHelper.waitAll(futures);
        } finally {
            executor.destroy();
        }
    }

    private Map<String, ExportTableConfig> getAllTables() {
        Map<String, ExportTableConfig> map = new LinkedHashMap<>();
        if (config.getTables() != null) {
            config.getTables().forEach(table -> map.put(StringHelper.lowerCase(table.getName()), table));
        }

        if (config.isExportAllTables() || !StringHelper.isEmpty(config.getTableNamePrefix())) {
            String tableNamePattern = config.isExportAllTables() ? null : config.getTableNamePrefix() + "%";

            DataBaseMeta meta = new JdbcMetaDiscovery().discover(config.buildDataSource(), config.getCatalog(),
                    null, tableNamePattern);

            for (OrmEntityModel table : meta.getTables().values()) {
                String name = StringHelper.lowerCase(table.getTableName());

                if (config.getExcludeTableNames() != null && config.getExcludeTableNames().contains(name))
                    continue;

                if (!map.containsKey(name)) {
                    ExportTableConfig tableConfig = new ExportTableConfig();
                    tableConfig.setName(name);
                    map.put(name, tableConfig);
                }
            }
        }
        return map;
    }

    private void runTask(ExportTableConfig tableConfig, DataSource ds) {
        BatchTaskBuilder builder = new BatchTaskBuilder();
        builder.loader(newLoader(tableConfig, ds));
        builder.batchSize(config.getBatchSize());
        builder.consumer(newConsumer(tableConfig));

        IBatchTask task = builder.build();
        IBatchTaskContext context = new BatchTaskContextImpl();
        task.execute(context);
    }

    private IBatchLoader<Map<String, Object>, IBatchChunkContext> newLoader(ExportTableConfig tableConfig,
                                                                            DataSource ds) {
        JdbcBatchLoader<Map<String, Object>> loader = new JdbcBatchLoader<>();
        loader.setDataSource(ds);
        loader.setSql(tableConfig.buildSQL());
        return loader;
    }

    private IBatchConsumer<Map<String, Object>, IBatchChunkContext> newConsumer(ExportTableConfig tableConfig) {
        List<IBatchConsumer<Map<String, Object>, IBatchChunkContext>> list = new ArrayList<>();
        if (config.getExportFormats() != null) {
            for (String format : config.getExportFormats()) {
                if ("sql".equals(format)) {
                    list.add(newGenSqlConsumer(tableConfig, format));
                } else if ("csv".equals(format) || "csv.gz".equals(format)) {
                    list.add(newCsvConsumer(tableConfig, format));
                } else {
                    throw new IllegalArgumentException("nop.err.dbtool.invalid-exp-format:" + format);
                }
            }
        }
        if (list.isEmpty())
            return newCsvConsumer(tableConfig, "csv");
        return new MultiBatchConsumer<>(list);
    }

    private IBatchConsumer<Map<String, Object>, IBatchChunkContext> newCsvConsumer(ExportTableConfig tableConfig, String format) {
        CsvResourceRecordIO<Map<String, Object>> recordIO = new CsvResourceRecordIO<>();
        recordIO.setRecordType(Map.class);
        recordIO.setSupportZip(true);

        return newResourceConsumer(recordIO, tableConfig, format);
    }

    private IBatchConsumer<Map<String, Object>, IBatchChunkContext> newGenSqlConsumer(ExportTableConfig tableConfig, String format) {
        GenInsertSqlRecordIO recordIO = new GenInsertSqlRecordIO();
        recordIO.setDialect(dialect.getName());
        return newResourceConsumer(recordIO, tableConfig, format);
    }

    private <S, C> ResourceRecordConsumer<S, C> newResourceConsumer(IResourceRecordIO<S> recordIO,
                                                                    ExportTableConfig tableConfig, String format) {
        ResourceRecordConsumer<S, C> consumer = new ResourceRecordConsumer<>();
        consumer.setRecordIO(recordIO);
        consumer.setResourcePath(tableConfig.getName() + "." + format);
        consumer.setResourceLoader(outputResourceLoader);
        return consumer;
    }
}
