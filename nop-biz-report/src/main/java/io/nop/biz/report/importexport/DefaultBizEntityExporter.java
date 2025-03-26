package io.nop.biz.report.importexport;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.crud.BizObjHelper;
import io.nop.biz.report.BizReportConstants;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmEntity;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class DefaultBizEntityExporter implements IBizEntityExporter {
    static final Logger LOG = LoggerFactory.getLogger(DefaultBizEntityExporter.class);

    private IGraphQLEngine graphQLEngine;
    private IDaoProvider daoProvider;
    private IBizObjectManager bizObjManager;
    private String executorBean;
    private int keepTempFileMinutes = 10;
    private int maxConcurrency = 1;
    private int maxBatchSize = 1000;

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setBizObjManager(IBizObjectManager bizObjManager) {
        this.bizObjManager = bizObjManager;
    }

    public void setExecutorBean(String executorBean) {
        this.executorBean = executorBean;
    }

    @InjectValue("@cfg:nop.biz.report.export.max-concurrency|1")
    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    @InjectValue("@cfg:nop.biz.report.keep-temp-file-minutes|10")
    public void setKeepTempFileMinutes(int keepTempFileMinutes) {
        this.keepTempFileMinutes = keepTempFileMinutes;
    }

    @InjectValue("@cfg:nop.biz.report.export.max-batch-size|1000")
    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public CompletionStage<WebContentBean> exportByQuery(String bizObjName, BizEntityExportConfig config,
                                                         FieldSelectionBean selection, IServiceContext context) {
        IBizObject bizObj = bizObjManager.getBizObject(bizObjName);
        IEntityDao<IOrmEntity> dao = BizObjHelper.getDao(bizObj, context);

        if (config.getConcurrency() > maxConcurrency)
            config.setConcurrency(maxConcurrency);

        if (config.getBatchSize() > maxBatchSize)
            config.setBatchSize(maxBatchSize);

        LOG.info("nop.biz.export-by-query:bizObjName={},concurrency={},batchSize={}", bizObjName,
                config.getConcurrency(), config.getBatchSize());

        Executor executor = getExecutor(config);
        BizExportTaskBuilder builder = new BizExportTaskBuilder(graphQLEngine, daoProvider, executor);
        IBatchTask task = builder.newTaskBuilder(bizObjName, dao, config).buildTask();
        IBatchTaskContext taskCtx = new BatchTaskContextImpl(context);

        IResource tempResource = ResourceHelper.getTempResource("export");
        taskCtx.setParams(Map.of(BizReportConstants.VAR_EXPORT_FILE_PATH, tempResource.getPath()));

        return task.executeAsync(taskCtx).thenApply(ret -> {
            return buildWebContentBean(taskCtx, config, tempResource);
        }).whenComplete((ret, err) -> {
            cleanup(err, tempResource);
        });
    }

    protected void cleanup(Throwable err, IResource tempResource) {
        // 出错时删除。成功下载时需要延迟删除
        if (err != null) {
            tempResource.delete();
        } else {
            GlobalExecutors.globalTimer().schedule(() -> {
                tempResource.delete();
                return null;
            }, keepTempFileMinutes, TimeUnit.MINUTES);
        }
    }

    protected Executor getExecutor(BizEntityExportConfig config) {
        if (executorBean == null) {
            if (config.getConcurrency() > 1)
                return GlobalExecutors.globalWorker();
            return GlobalExecutors.syncExecutor();
        }
        if (BeanContainer.instance().containsBean(executorBean))
            return (Executor) BeanContainer.instance().getBean(executorBean);
        return GlobalExecutors.requireExecutor(executorBean);
    }

    protected WebContentBean buildWebContentBean(IBatchTaskContext taskCtx,
                                                 BizEntityExportConfig config,
                                                 IResource tempResource) {
        WebContentBean contentBean = new WebContentBean(WebContentBean.CONTENT_TYPE_OCTET, tempResource, config.getExportFileName());
        return contentBean;
    }
}
