package io.nop.batch.biz.importexport;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.biz.api.IBizObject;
import io.nop.biz.crud.BizObjHelper;
import io.nop.batch.biz.BizReportConstants;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class DefaultBizEntityExporter extends AbstractBizEntityService implements IBizEntityExporter {
    static final Logger LOG = LoggerFactory.getLogger(DefaultBizEntityExporter.class);

    private int keepTempFileMinutes = 10;

    @InjectValue("@cfg:nop.biz.report.keep-temp-file-minutes|10")
    public void setKeepTempFileMinutes(int keepTempFileMinutes) {
        this.keepTempFileMinutes = keepTempFileMinutes;
    }

    @Override
    public CompletionStage<WebContentBean> exportByQuery(String bizObjName, BizEntityExportConfig config,
                                                         FieldSelectionBean selection, IServiceContext context) {
        IBizObject bizObj = bizObjManager.getBizObject(bizObjName);
        IEntityDao<IOrmEntity> dao = BizObjHelper.getDao(bizObj, context);

        clampConfig(config, maxConcurrency, maxBatchSize);

        LOG.info("nop.biz.export-by-query:bizObjName={},concurrency={},batchSize={}", bizObjName,
                config.getConcurrency(), config.getBatchSize());

        Executor executor = getExecutor(config);
        BizExportTaskBuilder builder = new BizExportTaskBuilder(graphQLEngine, daoProvider, executor);
        IBatchTask task = builder.newTaskBuilder(bizObjName, dao, config).buildTask();
        IBatchTaskContext taskCtx = new BatchTaskContextImpl(context);
        if (config.getMetaData() != null)
            taskCtx.addAttributes(config.getMetaData());

        IResource tempResource = ResourceHelper.getTempResource("export");
        taskCtx.setParam(BizReportConstants.VAR_EXPORT_FILE_PATH, tempResource.getPath());

        return task.executeAsync(taskCtx).thenApply(ret -> {
            return buildWebContentBean(taskCtx, config, tempResource);
        }).whenComplete((ret, err) -> {
            cleanup(err, tempResource);
        });
    }

    /**
     * 1.7-C8-010（plan 2546 W-11）：导出并发/批量的**上下界**钳制（package-private 纯函数，供单测直测）。
     * <p>
     * 原实现只判 {@code >} 上界；client 传 {@code batchSize=0/负} 会原样穿透到 builder，
     * 经 {@code adjustBatchSize} 钳到 1 → 每行一 chunk（日志/状态文件写放大）。
     * 下界归一后与 {@code BatchTask.normalizeConcurrency} 构成双保险。
     */
    static void clampConfig(BizEntityExportConfig config, int maxConcurrency, int maxBatchSize) {
        if (config.getConcurrency() > maxConcurrency)
            config.setConcurrency(maxConcurrency);
        if (config.getConcurrency() <= 0)
            config.setConcurrency(1);

        if (config.getBatchSize() > maxBatchSize)
            config.setBatchSize(maxBatchSize);
        if (config.getBatchSize() <= 0)
            config.setBatchSize(maxBatchSize);
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

    protected WebContentBean buildWebContentBean(IBatchTaskContext taskCtx,
                                                 BizEntityExportConfig config,
                                                 IResource tempResource) {
        WebContentBean contentBean = new WebContentBean(WebContentBean.CONTENT_TYPE_OCTET, tempResource, config.getExportFileName());
        return contentBean;
    }
}
